package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Order(20) — Detects and validates the authentication mechanism used in the request.
 * Supports API Key (X-API-Key header), JWT (Authorization: Bearer), and anonymous access.
 * Populates the Spring SecurityContext with a {@link GatewayAuthentication}.
 */
@Slf4j
@Component
@Order(20)
public class GatewayAuthFilter implements Filter {

    public static final String ATTR_AUTH_TYPE = "gateway.authType";
    public static final String ATTR_ENVIRONMENT = "gateway.environment";

    public static final String AUTH_TYPE_API_KEY = "API_KEY";
    public static final String AUTH_TYPE_JWT = "JWT";
    public static final String AUTH_TYPE_ANONYMOUS = "ANONYMOUS";

    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Cache<String, ApiKeyInfo> apiKeyCache;

    public GatewayAuthFilter(JwtDecoder jwtDecoder,
                             ObjectMapper objectMapper,
                             @Value("${gateway.runtime.identity-service-url}") String identityServiceUrl) {
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
        this.apiKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // ALWAYS clear SecurityContext at the start — virtual threads reuse ThreadLocals
        SecurityContextHolder.clearContext();

        // Check if a prior filter in THIS request already authenticated (via request attribute).
        if (request.getAttribute(ATTR_AUTH_TYPE) != null
                && request.getAttribute("gateway.consumerId") != null) {
            log.info("Auth already set by prior filter (type={}), skipping GatewayAuthFilter",
                    request.getAttribute(ATTR_AUTH_TYPE));
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        String authHeader = request.getHeader("Authorization");

        try {
            if (apiKey != null && !apiKey.isBlank()) {
                handleApiKeyAuth(request, apiKey);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                handleJwtAuth(request, authHeader.substring(7));
            } else {
                handleAnonymousAuth(request);
            }
        } catch (AuthenticationFailedException e) {
            log.warn("Authentication failed: {}", e.getMessage());
            writeErrorResponse(response, request, e.getErrorCode(), e.getMessage());
            return;
        } finally {
            // Clear security context after the request completes
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void handleApiKeyAuth(HttpServletRequest request, String apiKey) {
        String keyHash = hashApiKey(apiKey);

        ApiKeyInfo keyInfo = apiKeyCache.get(keyHash, hash -> {
            log.debug("Cache miss for API key hash, calling identity-service");
            return validateKeyWithIdentityService(hash);
        });

        if (keyInfo == null || !keyInfo.active()) {
            throw new AuthenticationFailedException("AUTH_API_KEY_INVALID", "Invalid or inactive API key");
        }
        log.debug("API key resolved: appId={}, active={}", keyInfo.appId(), keyInfo.active());

        GatewayAuthentication authentication = new GatewayAuthentication(
                keyInfo.appId(),
                keyInfo.orgId(),
                null,       // no email for API-key auth
                keyInfo.roles() != null ? keyInfo.roles() : Collections.emptyList(),
                keyInfo.permissions() != null ? keyInfo.permissions() : Collections.emptyList(),
                keyInfo.appId(),
                keyInfo.scope()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(ATTR_AUTH_TYPE, AUTH_TYPE_API_KEY);
        request.setAttribute(ATTR_ENVIRONMENT, keyInfo.environmentSlug() != null ? keyInfo.environmentSlug() : "dev");
        request.setAttribute("gateway.consumerId", keyInfo.appId());
        request.setAttribute("gateway.applicationId", keyInfo.appId());
        log.debug("API key authenticated: consumerId={}, appId={}, orgId={}", keyInfo.appId(), keyInfo.appId(), keyInfo.orgId());
    }

    private void handleJwtAuth(HttpServletRequest request, String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new AuthenticationFailedException("AUTH_JWT_INVALID", "Invalid or expired JWT: " + e.getMessage());
        }

        String userId = jwt.getSubject();
        String orgId = jwt.getClaimAsString("org_id");
        String email = jwt.getClaimAsString("email");
        String appId = jwt.getClaimAsString("app_id");

        List<String> roles = extractListClaim(jwt, "roles");
        List<String> permissions = extractListClaim(jwt, "permissions");
        String scope = jwt.getClaimAsString("scope");

        GatewayAuthentication authentication = new GatewayAuthentication(
                userId, orgId, email, roles, permissions, appId, scope
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(ATTR_AUTH_TYPE, AUTH_TYPE_JWT);
        request.setAttribute("gateway.consumerId", userId);
        if (appId != null) request.setAttribute("gateway.applicationId", appId);
        log.debug("JWT authenticated: sub={}, orgId={}", userId, orgId);
    }

    private void handleAnonymousAuth(HttpServletRequest request) {
        request.setAttribute(ATTR_AUTH_TYPE, AUTH_TYPE_ANONYMOUS);
        log.debug("No credentials provided — anonymous access");
    }

    // ThreadLocal avoids MessageDigest.getInstance() lookup per request
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 not available", e); }
    });
    private static final HexFormat HEX = HexFormat.of();

    private String hashApiKey(String apiKey) {
        MessageDigest digest = SHA256_DIGEST.get();
        digest.reset();
        byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(hash);
    }

    private ApiKeyInfo validateKeyWithIdentityService(String keyHash) {
        try {
            return restClient.get()
                    .uri("/v1/internal/validate-key?keyHash={hash}", keyHash)
                    .retrieve()
                    .body(ApiKeyInfo.class);
        } catch (Exception e) {
            log.error("Failed to validate API key with identity-service: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractListClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (claim instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
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

    /**
     * Record returned by the identity-service key validation endpoint.
     * Field names must match the JSON from {@code ValidateKeyResponse}.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record ApiKeyInfo(
            @com.fasterxml.jackson.annotation.JsonProperty("applicationId") String appId,
            @com.fasterxml.jackson.annotation.JsonProperty("orgId") String orgId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("environmentSlug") String environmentSlug,
            List<String> roles,
            List<String> permissions,
            String scope
    ) {
        boolean active() {
            return "ACTIVE".equals(status) || "ROTATED".equals(status);
        }
    }

    /**
     * Internal exception to signal authentication failure with a specific error code.
     */
    private static class AuthenticationFailedException extends RuntimeException {
        private final String errorCode;

        AuthenticationFailedException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        String getErrorCode() {
            return errorCode;
        }
    }
}
