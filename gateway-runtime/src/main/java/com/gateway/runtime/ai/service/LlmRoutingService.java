package com.gateway.runtime.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.config.LlmProviderConfig.ProviderSettings;
import com.gateway.runtime.ai.model.LlmProvider;
import com.gateway.runtime.ai.model.LlmRequest;
import com.gateway.runtime.ai.model.LlmResponse;
import com.gateway.runtime.ai.model.LlmResponse.LlmUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Universal LLM routing service that translates between a unified request format
 * and provider-specific APIs, with fallback chain support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRoutingService {

    private final LlmProviderConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Route an LLM request to the best available provider.
     * Selection order: preferred provider > fallback chain > weighted random.
     * On failure, the next provider in the fallback chain is tried.
     */
    public LlmResponse route(LlmRequest request, String preferredProvider) {
        List<String> providerChain = buildProviderChain(preferredProvider);

        for (String providerName : providerChain) {
            ProviderSettings settings = config.getProviders().get(providerName);
            if (settings == null || !settings.isEnabled()) {
                continue;
            }
            try {
                long start = System.currentTimeMillis();
                LlmResponse response = callProvider(providerName, settings, request);
                response.setLatencyMs(System.currentTimeMillis() - start);
                response.setProvider(providerName);
                return response;
            } catch (Exception ex) {
                log.warn("Provider {} failed, trying next in fallback chain: {}", providerName, ex.getMessage());
            }
        }

        throw new RuntimeException("All LLM providers failed for request model=" + request.getModel());
    }

    /**
     * List all configured and enabled provider names with their default models.
     */
    public List<Map<String, Object>> listProviders() {
        List<Map<String, Object>> result = new ArrayList<>();
        config.getProviders().forEach((name, settings) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", name);
            info.put("enabled", settings.isEnabled());
            info.put("defaultModel", settings.getDefaultModel());
            info.put("apiUrl", settings.getApiUrl());
            result.add(info);
        });
        return result;
    }

    /**
     * List available models across all enabled providers.
     */
    public List<Map<String, String>> listModels() {
        List<Map<String, String>> models = new ArrayList<>();
        config.getProviders().forEach((name, settings) -> {
            if (settings.isEnabled() && settings.getDefaultModel() != null) {
                models.add(Map.of("provider", name, "model", settings.getDefaultModel()));
            }
        });
        return models;
    }

    // ── Provider chain ──────────────────────────────────────────────────

    private List<String> buildProviderChain(String preferred) {
        List<String> chain = new ArrayList<>();
        if (preferred != null && !preferred.isBlank()) {
            chain.add(preferred.toLowerCase());
        }
        if (config.getFallbackChain() != null) {
            for (String p : config.getFallbackChain()) {
                String lower = p.toLowerCase().trim();
                if (!chain.contains(lower)) {
                    chain.add(lower);
                }
            }
        }
        // Add any remaining enabled providers by weighted random
        List<String> remaining = new ArrayList<>();
        config.getProviders().forEach((name, settings) -> {
            if (settings.isEnabled() && !chain.contains(name.toLowerCase())) {
                remaining.add(name.toLowerCase());
            }
        });
        Collections.shuffle(remaining, ThreadLocalRandom.current());
        chain.addAll(remaining);
        return chain;
    }

    // ── Provider dispatch ───────────────────────────────────────────────

    private LlmResponse callProvider(String providerName, ProviderSettings settings, LlmRequest request) {
        LlmProvider provider = resolveProviderType(providerName);
        String model = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel()
                : settings.getDefaultModel();

        return switch (provider) {
            case OPENAI, DEEPSEEK, AZURE_OPENAI, SELF_HOSTED -> callOpenAICompatible(settings, request, model, providerName);
            case ANTHROPIC -> callAnthropic(settings, request, model);
            case GOOGLE_GEMINI -> callGemini(settings, request, model);
            case MISTRAL -> callOpenAICompatible(settings, request, model, providerName);
            case AWS_BEDROCK -> callOpenAICompatible(settings, request, model, providerName);
        };
    }

    private LlmProvider resolveProviderType(String name) {
        return switch (name.toLowerCase()) {
            case "openai" -> LlmProvider.OPENAI;
            case "anthropic" -> LlmProvider.ANTHROPIC;
            case "google_gemini", "gemini" -> LlmProvider.GOOGLE_GEMINI;
            case "deepseek" -> LlmProvider.DEEPSEEK;
            case "aws_bedrock", "bedrock" -> LlmProvider.AWS_BEDROCK;
            case "azure_openai", "azure" -> LlmProvider.AZURE_OPENAI;
            case "mistral" -> LlmProvider.MISTRAL;
            default -> LlmProvider.SELF_HOSTED;
        };
    }

    // ── OpenAI-compatible (OpenAI, DeepSeek, Azure, Mistral, Self-hosted) ──

    @SuppressWarnings("unchecked")
    private LlmResponse callOpenAICompatible(ProviderSettings settings, LlmRequest request, String model, String providerName) {
        Map<String, Object> body = translateToOpenAI(request, model);

        String responseJson = restClient.post()
                .uri(settings.getApiUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return translateFromOpenAI(responseJson, providerName);
    }

    private Map<String, Object> translateToOpenAI(LlmRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (LlmRequest.Message msg : request.getMessages()) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }
        body.put("messages", messages);
        body.put("temperature", request.getTemperature());
        body.put("max_tokens", request.getMaxTokens());
        if (request.isStream()) {
            body.put("stream", true);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse translateFromOpenAI(String responseJson, String providerName) {
        try {
            Map<String, Object> resp = objectMapper.readValue(responseJson, new TypeReference<>() {});
            String id = (String) resp.getOrDefault("id", "");
            String model = (String) resp.getOrDefault("model", "");

            String content = "";
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    content = (String) message.getOrDefault("content", "");
                }
            }

            LlmUsage usage = extractUsage(resp);

            return LlmResponse.builder()
                    .id(id)
                    .model(model)
                    .content(content)
                    .usage(usage)
                    .provider(providerName)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    // ── Anthropic Messages API ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private LlmResponse callAnthropic(ProviderSettings settings, LlmRequest request, String model) {
        Map<String, Object> body = translateToAnthropic(request, model);

        String responseJson = restClient.post()
                .uri(settings.getApiUrl() + "/messages")
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return translateFromAnthropic(responseJson);
    }

    private Map<String, Object> translateToAnthropic(LlmRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", request.getMaxTokens());

        List<Map<String, String>> messages = new ArrayList<>();
        String systemPrompt = null;
        if (request.getMessages() != null) {
            for (LlmRequest.Message msg : request.getMessages()) {
                if ("system".equals(msg.getRole())) {
                    systemPrompt = msg.getContent();
                } else {
                    messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }
        }
        body.put("messages", messages);
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }
        body.put("temperature", request.getTemperature());
        return body;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse translateFromAnthropic(String responseJson) {
        try {
            Map<String, Object> resp = objectMapper.readValue(responseJson, new TypeReference<>() {});
            String id = (String) resp.getOrDefault("id", "");
            String model = (String) resp.getOrDefault("model", "");

            String content = "";
            List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) resp.get("content");
            if (contentBlocks != null && !contentBlocks.isEmpty()) {
                content = (String) contentBlocks.get(0).getOrDefault("text", "");
            }

            Map<String, Object> usageMap = (Map<String, Object>) resp.get("usage");
            LlmUsage usage = LlmUsage.builder().build();
            if (usageMap != null) {
                int input = ((Number) usageMap.getOrDefault("input_tokens", 0)).intValue();
                int output = ((Number) usageMap.getOrDefault("output_tokens", 0)).intValue();
                usage = LlmUsage.builder()
                        .promptTokens(input)
                        .completionTokens(output)
                        .totalTokens(input + output)
                        .build();
            }

            return LlmResponse.builder()
                    .id(id)
                    .model(model)
                    .content(content)
                    .usage(usage)
                    .provider("anthropic")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    // ── Google Gemini ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private LlmResponse callGemini(ProviderSettings settings, LlmRequest request, String model) {
        Map<String, Object> body = translateToGemini(request);

        String url = settings.getApiUrl() + "/models/" + model + ":generateContent?key=" + settings.getApiKey();
        String responseJson = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return translateFromGemini(responseJson, model);
    }

    private Map<String, Object> translateToGemini(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();

        if (request.getMessages() != null) {
            for (LlmRequest.Message msg : request.getMessages()) {
                if ("system".equals(msg.getRole())) {
                    continue; // Gemini handles system prompts differently
                }
                String role = "user".equals(msg.getRole()) ? "user" : "model";
                Map<String, Object> part = Map.of("text", msg.getContent());
                contents.add(Map.of("role", role, "parts", List.of(part)));
            }
        }
        body.put("contents", contents);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", request.getTemperature());
        generationConfig.put("maxOutputTokens", request.getMaxTokens());
        body.put("generationConfig", generationConfig);

        return body;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse translateFromGemini(String responseJson, String model) {
        try {
            Map<String, Object> resp = objectMapper.readValue(responseJson, new TypeReference<>() {});

            String content = "";
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> contentObj = (Map<String, Object>) candidates.get(0).get("content");
                if (contentObj != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) contentObj.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        content = (String) parts.get(0).getOrDefault("text", "");
                    }
                }
            }

            Map<String, Object> usageMetadata = (Map<String, Object>) resp.get("usageMetadata");
            LlmUsage usage = LlmUsage.builder().build();
            if (usageMetadata != null) {
                int prompt = ((Number) usageMetadata.getOrDefault("promptTokenCount", 0)).intValue();
                int completion = ((Number) usageMetadata.getOrDefault("candidatesTokenCount", 0)).intValue();
                usage = LlmUsage.builder()
                        .promptTokens(prompt)
                        .completionTokens(completion)
                        .totalTokens(prompt + completion)
                        .build();
            }

            return LlmResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .model(model)
                    .content(content)
                    .usage(usage)
                    .provider("gemini")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    // ── Usage extraction helper ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private LlmUsage extractUsage(Map<String, Object> resp) {
        Map<String, Object> usageMap = (Map<String, Object>) resp.get("usage");
        if (usageMap == null) {
            return LlmUsage.builder().build();
        }
        int prompt = ((Number) usageMap.getOrDefault("prompt_tokens", 0)).intValue();
        int completion = ((Number) usageMap.getOrDefault("completion_tokens", 0)).intValue();
        int total = ((Number) usageMap.getOrDefault("total_tokens", prompt + completion)).intValue();
        return LlmUsage.builder()
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .build();
    }
}
