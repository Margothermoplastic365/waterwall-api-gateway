package com.gateway.runtime.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for AI/LLM gateway providers and safety settings.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.ai")
public class LlmProviderConfig {

    private boolean enabled = true;
    private String defaultProvider = "openai";
    private List<String> fallbackChain = List.of("openai", "anthropic", "deepseek");

    private Map<String, ProviderSettings> providers = new HashMap<>();

    private SafetySettings safety = new SafetySettings();
    private CacheSettings cache = new CacheSettings();

    @Data
    public static class ProviderSettings {
        private String apiUrl;
        private String apiKey;
        private String defaultModel;
        private boolean enabled = true;
        private int weight = 1;
    }

    @Data
    public static class SafetySettings {
        private boolean blockOnInjection = true;
        private boolean redactPii = true;
        private boolean blockOnToxicity = false;
    }

    @Data
    public static class CacheSettings {
        private boolean enabled = true;
        private double similarityThreshold = 0.95;
    }
}
