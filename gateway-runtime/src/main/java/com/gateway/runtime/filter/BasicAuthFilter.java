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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;

/**
 * Order(18) — HTTP Basic authentication filter. Runs before mTLS and the main auth filter.
 * <p>
 * Looks for an {@code Authorization: Basic ...} header. If present, decodes the
 * Base64 credentials as {@code username:password} where username = application client ID
 * and password = basic auth secret.
 * <p>
 * The password is SHA-256 hashed and validated against the identity-service. On success,
 * a {@link GatewayAuthentication} is placed in the {@link SecurityContextHolder}.
 * <p>
 * If no Basic header is present, this filter is a no-op. If Basic header is present
 * but credentials are invalid, a 401 response with {@code WWW-Authenticate: Basic}
 * header is returned.
 */
@Slf4j
@Component
@Order(18)
public class BasicAuthFilter implements Filter {

    public static final String AUTH_TYPE_BASIC = "BASIC";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Cache<String, BasicAuthInfo> basicAuthCache;

    public BasicAuthFilter(
            ObjectMapper objectMapper,
            @Value("${gateway.runtime.identity-service-url}") String identityServiceUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
        this.basicAuthCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String encoded = authHeader.substring(6);
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                writeUnauthorized(response, request, "AUTH_BASIC_MALFORMED", "Malformed Basic auth header");
                return;
            }

            int colonIndex = decoded.indexOf(':');
            if (colonIndex < 0) {
                writeUnauthorized(response, request, "AUTH_BASIC_MALFORMED", "Malformed Basic auth credentials");
                return;
            }

            String clientId = decoded.substring(0, colonIndex);
            String password = decoded.substring(colonIndex + 1);
            String secretHash = sha256Hex(password);

            String cacheKey = clientId + ":" + secretHash;
            BasicAuthInfo authInfo = basicAuthCache.get(cacheKey, key -> {
                log.debug("Basic auth cache miss for clientId={}, calling identity-service", clientId);
                return validateBasicWithIdentityService(clientId, secretHash);
            });

            if (authInfo != null) {
                GatewayAuthentication authentication = new GatewayAuthentication(
                        authInfo.appId(),
                        authInfo.orgId(),
                        null,       // no email for basic auth
                        Collections.emptyList(),
                        Collections.emptyList(),
                        authInfo.appId(),
                        null
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, AUTH_TYPE_BASIC);
                request.setAttribute("gateway.consumerId", authInfo.appId());
                request.setAttribute("gateway.applicationId", authInfo.appId());

                log.debug("Basic auth authenticated: appId={}, clientId={}", authInfo.appId(), clientId);
            } else {
                writeUnauthorized(response, request, "AUTH_BASIC_INVALID", "Invalid basic auth credentials");
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private BasicAuthInfo validateBasicWithIdentityService(String clientId, String secretHash) {
        try {
            return restClient.get()
                    .uri("/v1/internal/validate-basic?clientId={id}&secretHash={hash}", clientId, secretHash)
                    .retrieve()
                    .body(BasicAuthInfo.class);
        } catch (Exception e) {
            log.error("Failed to validate basic auth with identity-service: {}", e.getMessage());
            return null;
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request,
                                   String errorCode, String message) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Gateway API\"");
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
     * Record returned by the identity-service basic auth validation endpoint.
     */
    record BasicAuthInfo(
            String appId,
            String applicationName,
            String userId,
            String orgId
    ) {}
}
