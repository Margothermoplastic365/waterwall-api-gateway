package com.gateway.runtime.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.model.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Semantic caching service for LLM responses.
 * MVP uses hash-based exact matching on normalized prompts.
 * TODO: Add pgvector embedding-based similarity search for true semantic matching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmProviderConfig config;

    // Common English stop words for normalization
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "and", "but", "or",
            "not", "no", "nor", "so", "yet", "both", "either", "neither",
            "this", "that", "these", "those", "it", "its"
    );

    /**
     * Find a cached response similar to the given prompt.
     *
     * @param prompt    the user prompt
     * @param threshold similarity threshold (unused in hash MVP; reserved for vector search)
     * @return cached response if found
     */
    public Optional<LlmResponse> findSimilar(String prompt, double threshold) {
        if (!config.getCache().isEnabled()) {
            return Optional.empty();
        }

        String hash = hashPrompt(prompt);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT response_json, model, provider FROM analytics.semantic_cache " +
                            "WHERE prompt_hash = ? LIMIT 1",
                    hash
            );

            if (rows.isEmpty()) {
                return Optional.empty();
            }

            // Update hit count and last_hit_at
            jdbcTemplate.update(
                    "UPDATE analytics.semantic_cache SET hit_count = hit_count + 1, last_hit_at = now() WHERE prompt_hash = ?",
                    hash
            );

            String responseJson = (String) rows.get(0).get("response_json");
            LlmResponse cached = objectMapper.readValue(responseJson, LlmResponse.class);
            log.debug("Semantic cache hit for prompt hash {}", hash);
            return Optional.of(cached);
        } catch (Exception ex) {
            log.warn("Failed to query semantic cache: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cache an LLM response for future retrieval.
     */
    public void cacheResponse(String prompt, LlmResponse response) {
        if (!config.getCache().isEnabled()) {
            return;
        }

        String hash = hashPrompt(prompt);

        try {
            String responseJson = objectMapper.writeValueAsString(response);

            jdbcTemplate.update(
                    "INSERT INTO analytics.semantic_cache (id, prompt_hash, prompt_text, response_json, model, provider, hit_count, created_at, last_hit_at) " +
                            "VALUES (?::uuid, ?, ?, ?, ?, ?, 0, now(), now()) " +
                            "ON CONFLICT (prompt_hash) DO UPDATE SET response_json = EXCLUDED.response_json, last_hit_at = now()",
                    UUID.randomUUID().toString(), hash, prompt, responseJson,
                    response.getModel(), response.getProvider()
            );

            log.debug("Cached LLM response for prompt hash {}", hash);
        } catch (Exception ex) {
            log.warn("Failed to cache LLM response: {}", ex.getMessage());
        }
    }

    // ── Prompt normalization and hashing ────────────────────────────────

    /**
     * Normalize the prompt (lowercase, trim, remove stop words) and compute SHA-256 hash.
     */
    String hashPrompt(String prompt) {
        String normalized = normalizePrompt(prompt);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is always available; this should never happen
            throw new RuntimeException("Failed to hash prompt", e);
        }
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        String[] words = prompt.toLowerCase().trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String cleaned = word.replaceAll("[^a-z0-9]", "");
            if (!cleaned.isEmpty() && !STOP_WORDS.contains(cleaned)) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(cleaned);
            }
        }
        return sb.toString();
    }
}
