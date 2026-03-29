package com.gateway.runtime.ai.controller;

import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.filter.AiContentSafetyFilter;
import com.gateway.runtime.ai.model.LlmRequest;
import com.gateway.runtime.ai.model.LlmResponse;
import com.gateway.runtime.ai.model.SafetyCheckResult;
import com.gateway.runtime.ai.model.TokenBudgetStatus;
import com.gateway.runtime.ai.service.LlmRoutingService;
import com.gateway.runtime.ai.service.SemanticCacheService;
import com.gateway.runtime.ai.service.TokenRateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing the unified AI/LLM Gateway API.
 * Provides chat completions, text completions, model listing, and provider status.
 */
@Slf4j
@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
public class LlmGatewayController {

    private final LlmRoutingService routingService;
    private final AiContentSafetyFilter safetyFilter;
    private final TokenRateLimitService tokenRateLimitService;
    private final SemanticCacheService semanticCacheService;
    private final LlmProviderConfig config;

    /**
     * POST /v1/ai/chat/completions — unified chat endpoint.
     * Accepts a unified LlmRequest and routes to the best available provider.
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestBody LlmRequest request,
            @RequestHeader(value = "X-Consumer-Id", required = false) String consumerId,
            @RequestHeader(value = "X-LLM-Provider", required = false) String preferredProvider) {

        if (!config.isEnabled()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "AI/LLM gateway is disabled"));
        }

        // Content safety check
        SafetyCheckResult safetyResult = safetyFilter.filterRequest(request);
        if (safetyResult.isBlocked()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "content_safety_violation",
                            "violations", safetyResult.getViolations()));
        }

        // Token budget check
        if (consumerId != null) {
            int estimated = estimateTokens(request);
            if (!tokenRateLimitService.checkTokenBudget(consumerId, estimated)) {
                return ResponseEntity.status(429)
                        .body(Map.of("error", "token_budget_exceeded",
                                "budget", tokenRateLimitService.getBudgetStatus(consumerId)));
            }
        }

        // Semantic cache lookup (non-streaming only)
        if (!request.isStream()) {
            String prompt = extractFullPrompt(request);
            Optional<LlmResponse> cached = semanticCacheService.findSimilar(
                    prompt, config.getCache().getSimilarityThreshold());
            if (cached.isPresent()) {
                return ResponseEntity.ok(cached.get());
            }
        }

        // SSE streaming support
        if (request.isStream()) {
            return handleStreamingRequest(request, preferredProvider);
        }

        // Route to provider
        try {
            String provider = preferredProvider != null ? preferredProvider : config.getDefaultProvider();
            LlmResponse response = routingService.route(request, provider);

            // Response safety filter
            safetyFilter.filterResponse(response);

            // Record usage
            if (consumerId != null && response.getUsage() != null) {
                tokenRateLimitService.recordTokenUsage(
                        consumerId, response.getModel(), response.getProvider(),
                        response.getUsage().getPromptTokens(),
                        response.getUsage().getCompletionTokens()
                );
            }

            // Cache the response
            String prompt = extractFullPrompt(request);
            semanticCacheService.cacheResponse(prompt, response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Chat completion failed: {}", e.getMessage());
            return ResponseEntity.status(502)
                    .body(Map.of("error", "provider_error", "message", e.getMessage()));
        }
    }

    /**
     * POST /v1/ai/completions — text completion (delegates to chat with a single user message).
     */
    @PostMapping("/completions")
    public ResponseEntity<?> textCompletions(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Consumer-Id", required = false) String consumerId,
            @RequestHeader(value = "X-LLM-Provider", required = false) String preferredProvider) {

        String prompt = (String) body.getOrDefault("prompt", "");
        String model = (String) body.get("model");
        double temperature = body.containsKey("temperature")
                ? ((Number) body.get("temperature")).doubleValue() : 0.7;
        int maxTokens = body.containsKey("max_tokens")
                ? ((Number) body.get("max_tokens")).intValue() : 1024;

        LlmRequest request = LlmRequest.builder()
                .model(model)
                .messages(List.of(LlmRequest.Message.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(false)
                .build();

        return chatCompletions(request, consumerId, preferredProvider);
    }

    /**
     * GET /v1/ai/models — list available models across all providers.
     */
    @GetMapping("/models")
    public ResponseEntity<?> listModels() {
        return ResponseEntity.ok(Map.of("models", routingService.listModels()));
    }

    /**
     * GET /v1/ai/providers — list configured providers with status.
     */
    @GetMapping("/providers")
    public ResponseEntity<?> listProviders() {
        return ResponseEntity.ok(Map.of("providers", routingService.listProviders()));
    }

    // ── Streaming support ───────────────────────────────────────────────

    private ResponseEntity<?> handleStreamingRequest(LlmRequest request, String preferredProvider) {
        // For MVP, streaming falls back to non-streaming with SSE wrapper
        // TODO: Implement true SSE streaming with provider-specific SSE parsing
        try {
            String provider = preferredProvider != null ? preferredProvider : config.getDefaultProvider();
            // Disable streaming flag for the actual call (MVP)
            request.setStream(false);
            LlmResponse response = routingService.route(request, provider);

            // Return as SSE-formatted response
            String sseData = "data: " + "{\"id\":\"" + response.getId() + "\","
                    + "\"model\":\"" + response.getModel() + "\","
                    + "\"content\":\"" + escapeJson(response.getContent()) + "\"}\n\n"
                    + "data: [DONE]\n\n";

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseData);
        } catch (Exception e) {
            log.error("Streaming request failed: {}", e.getMessage());
            return ResponseEntity.status(502)
                    .body(Map.of("error", "streaming_error", "message", e.getMessage()));
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

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

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
