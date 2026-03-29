package com.gateway.runtime.ai.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.filter.AiContentSafetyFilter;
import com.gateway.runtime.ai.model.LlmRequest;
import com.gateway.runtime.ai.model.LlmResponse;
import com.gateway.runtime.ai.model.SafetyCheckResult;
import com.gateway.runtime.ai.service.LlmRoutingService;
import com.gateway.runtime.ai.service.SemanticCacheService;
import com.gateway.runtime.ai.service.TokenRateLimitService;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.proxy.ProtocolProxyHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Protocol proxy handler for LLM requests. Integrates content safety,
 * token rate limiting, semantic caching, and universal LLM routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProxyHandler implements ProtocolProxyHandler {

    private final LlmRoutingService routingService;
    private final AiContentSafetyFilter safetyFilter;
    private final TokenRateLimitService tokenRateLimitService;
    private final SemanticCacheService semanticCacheService;
    private final LlmProviderConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public String getProtocolType() {
        return "LLM";
    }

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute) {
        if (!config.isEnabled()) {
            return errorResponse(503, "AI/LLM gateway is disabled");
        }

        // 1. Parse incoming request
        LlmRequest llmRequest;
        try {
            byte[] body = request.getInputStream().readAllBytes();
            llmRequest = objectMapper.readValue(body, LlmRequest.class);
        } catch (IOException e) {
            log.error("Failed to parse LLM request: {}", e.getMessage());
            return errorResponse(400, "Invalid LLM request format");
        }

        // 2. Content safety check
        SafetyCheckResult safetyResult = safetyFilter.filterRequest(llmRequest);
        if (safetyResult.isBlocked()) {
            return errorResponse(400, "Request blocked by content safety: " + String.join(", ", safetyResult.getViolations()));
        }

        // 3. Check token budget
        String consumerId = request.getHeader("X-Consumer-Id");
        if (consumerId != null) {
            int estimatedTokens = estimateTokens(llmRequest);
            if (!tokenRateLimitService.checkTokenBudget(consumerId, estimatedTokens)) {
                return errorResponse(429, "Token budget exceeded");
            }
        }

        // 4. Check semantic cache (skip for streaming)
        if (!llmRequest.isStream()) {
            String fullPrompt = extractFullPrompt(llmRequest);
            Optional<LlmResponse> cached = semanticCacheService.findSimilar(
                    fullPrompt, config.getCache().getSimilarityThreshold());
            if (cached.isPresent()) {
                log.debug("Returning cached LLM response");
                return jsonResponse(cached.get());
            }
        }

        // 5. Route to LLM provider
        String preferredProvider = request.getHeader("X-LLM-Provider");
        if (preferredProvider == null) {
            preferredProvider = config.getDefaultProvider();
        }

        try {
            LlmResponse response = routingService.route(llmRequest, preferredProvider);

            // 6. Check response safety
            SafetyCheckResult responseSafety = safetyFilter.filterResponse(response);
            if (responseSafety.isBlocked()) {
                return errorResponse(500, "Response blocked by content safety");
            }

            // 7. Record token usage
            if (consumerId != null && response.getUsage() != null) {
                tokenRateLimitService.recordTokenUsage(
                        consumerId, response.getModel(), response.getProvider(),
                        response.getUsage().getPromptTokens(),
                        response.getUsage().getCompletionTokens()
                );
            }

            // 8. Cache response
            if (!llmRequest.isStream()) {
                String fullPrompt = extractFullPrompt(llmRequest);
                semanticCacheService.cacheResponse(fullPrompt, response);
            }

            return jsonResponse(response);
        } catch (Exception e) {
            log.error("LLM routing failed: {}", e.getMessage());
            return errorResponse(502, "All LLM providers failed: " + e.getMessage());
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private ResponseEntity<byte[]> jsonResponse(Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json);
        } catch (Exception e) {
            return errorResponse(500, "Failed to serialize response");
        }
    }

    private ResponseEntity<byte[]> errorResponse(int status, String message) {
        String json = "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json.getBytes());
    }

    private String extractFullPrompt(LlmRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LlmRequest.Message msg : request.getMessages()) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private int estimateTokens(LlmRequest request) {
        // Rough estimation: ~4 chars per token
        int charCount = 0;
        if (request.getMessages() != null) {
            for (LlmRequest.Message msg : request.getMessages()) {
                if (msg.getContent() != null) {
                    charCount += msg.getContent().length();
                }
            }
        }
        return Math.max(1, charCount / 4) + request.getMaxTokens();
    }
}
