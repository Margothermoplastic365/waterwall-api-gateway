package com.gateway.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Extracts an API key from the {@code X-API-Key} header or the {@code apikey}
 * query parameter and attempts to resolve it from the Caffeine-backed
 * {@code apiKeys} cache.
 * <p>
 * If the key is not present or not found in cache the filter simply continues
 * the chain — the gateway-runtime layer is responsible for the authoritative
 * DB lookup.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_QUERY_PARAM = "apikey";
    private static final String CACHE_NAME = "apiKeys";

    private final CacheManager cacheManager;

    public ApiKeyAuthenticationFilter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if an authentication is already established (e.g. by JwtAuthenticationFilter)
        if (SecurityContextHolder.getContext().getAuthentication() instanceof GatewayAuthentication) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String keyHash = sha256(apiKey);
            Cache cache = cacheManager.getCache(CACHE_NAME);

            if (cache != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> appInfo = cache.get(keyHash, Map.class);

                if (appInfo != null) {
                    String appId = stringValue(appInfo, "appId");
                    String orgId = stringValue(appInfo, "orgId");

                    @SuppressWarnings("unchecked")
                    List<String> permissions = appInfo.containsKey("permissions")
                            ? (List<String>) appInfo.get("permissions")
                            : Collections.emptyList();

                    GatewayAuthentication authentication = new GatewayAuthentication(
                            null,       // no user for API-key auth
                            orgId,
                            null,       // no email for API-key auth
                            Collections.emptyList(),
                            permissions,
                            appId,
                            null
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("API key hash not found in cache; deferring to gateway-runtime");
                }
            }
        } catch (Exception ex) {
            log.warn("Error during API-key authentication", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String param = request.getParameter(API_KEY_QUERY_PARAM);
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return null;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed by the JVM spec
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
