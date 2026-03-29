package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.dto.ApiErrorResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order(10) — First filter in the pipeline.
 * Checks whether the incoming credential (API key prefix or JWT jti) has been revoked.
 * Uses a Caffeine cache with a short TTL backed by a JdbcTemplate query against
 * {@code identity.revocation_list}.
 */
@Slf4j
@Component
@Order(10)
public class RevocationCheckFilter implements Filter {

    public static final String ATTR_REVOCATION_CHECKED = "gateway.revocationChecked";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Cache<String, Set<String>> revocationCache;

    private static final String CACHE_KEY = "revocation_list";

    private static final String QUERY_REVOCATION_LIST =
            "SELECT credential_id FROM identity.revocation_list WHERE expires_at > now()";

    public RevocationCheckFilter(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${gateway.runtime.revocation-list.cache-ttl-seconds:5}") int cacheTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.revocationCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(1)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        Set<String> revokedCredentials = loadRevocationList();

        // Check API key revocation
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            String keyPrefix = extractApiKeyPrefix(apiKey);
            if (revokedCredentials.contains(keyPrefix)) {
                log.warn("Revoked API key detected: prefix={}", keyPrefix);
                writeErrorResponse(response, request, "CREDENTIAL_REVOKED", "API key has been revoked");
                return;
            }
        }

        // Check JWT jti revocation
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jti = extractJtiFromToken(authHeader.substring(7));
            if (jti != null && revokedCredentials.contains(jti)) {
                log.warn("Revoked JWT detected: jti={}", jti);
                writeErrorResponse(response, request, "CREDENTIAL_REVOKED", "Token has been revoked");
                return;
            }
        }

        request.setAttribute(ATTR_REVOCATION_CHECKED, Boolean.TRUE);
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private Set<String> loadRevocationList() {
        return revocationCache.get(CACHE_KEY, key -> {
            log.debug("Loading revocation list from database");
            List<String> identifiers = jdbcTemplate.queryForList(QUERY_REVOCATION_LIST, String.class);
            return Set.copyOf(identifiers);
        });
    }

    /**
     * Extracts a stable prefix from the API key for revocation matching.
     * The prefix is the first 8 characters of the SHA-256 hash.
     */
    private String extractApiKeyPrefix(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts the jti (JWT ID) claim from the token payload without full validation.
     * Full validation happens in the next filter (GatewayAuthFilter).
     */
    private String extractJtiFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var node = objectMapper.readTree(payload);
            var jtiNode = node.get("jti");
            return jtiNode != null ? jtiNode.asText() : null;
        } catch (Exception e) {
            log.debug("Failed to extract jti from token: {}", e.getMessage());
            return null;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    String errorCode, String message) throws IOException {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .error("UNAUTHORIZED")
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .build();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
