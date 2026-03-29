package com.gateway.runtime.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified LLM request format that is translated to provider-specific formats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {

    private String model;
    private List<Message> messages;
    @Builder.Default
    private double temperature = 0.7;
    @Builder.Default
    private int maxTokens = 1024;
    @Builder.Default
    private boolean stream = false;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
