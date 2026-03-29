package com.gateway.runtime.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified LLM response translated from provider-specific formats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    private String id;
    private String model;
    private String content;
    private LlmUsage usage;
    private long latencyMs;
    private String provider;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
