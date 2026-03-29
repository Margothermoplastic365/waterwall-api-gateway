package com.gateway.runtime.ai.filter;

import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.model.LlmRequest;
import com.gateway.runtime.ai.model.LlmResponse;
import com.gateway.runtime.ai.model.SafetyCheckResult;
import com.gateway.runtime.ai.service.ContentSafetyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Content safety filter that wraps LLM proxy calls.
 * Checks prompts before sending and responses after receiving.
 * Can block requests or redact PII based on configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContentSafetyFilter {

    private final ContentSafetyService contentSafetyService;
    private final LlmProviderConfig config;

    /**
     * Check the request prompts for safety violations before sending to an LLM provider.
     *
     * @param request the LLM request
     * @return safety result; if blocked, the request should not proceed
     */
    public SafetyCheckResult filterRequest(LlmRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return SafetyCheckResult.builder()
                    .blocked(false)
                    .violations(List.of())
                    .severity("NONE")
                    .build();
        }

        // Check each message content
        for (LlmRequest.Message message : request.getMessages()) {
            SafetyCheckResult result = contentSafetyService.checkPrompt(message.getContent());
            if (result.isBlocked()) {
                log.warn("Request blocked by safety filter: {}", result.getViolations());
                return result;
            }

            // If PII was detected and redaction is enabled, update the message content
            if (config.getSafety().isRedactPii() && result.getRedactedContent() != null
                    && !result.getRedactedContent().equals(message.getContent())) {
                message.setContent(result.getRedactedContent());
                log.info("PII redacted in request message");
            }
        }

        return SafetyCheckResult.builder()
                .blocked(false)
                .violations(List.of())
                .severity("NONE")
                .build();
    }

    /**
     * Check the LLM response for safety violations.
     *
     * @param response the LLM response
     * @return safety result; content may be redacted
     */
    public SafetyCheckResult filterResponse(LlmResponse response) {
        if (response.getContent() == null || response.getContent().isBlank()) {
            return SafetyCheckResult.builder()
                    .blocked(false)
                    .violations(List.of())
                    .severity("NONE")
                    .build();
        }

        SafetyCheckResult result = contentSafetyService.checkResponse(response.getContent());

        // Redact PII in response if configured
        if (config.getSafety().isRedactPii() && result.getRedactedContent() != null
                && !result.getRedactedContent().equals(response.getContent())) {
            response.setContent(result.getRedactedContent());
            log.info("PII redacted in LLM response");
        }

        return result;
    }
}
