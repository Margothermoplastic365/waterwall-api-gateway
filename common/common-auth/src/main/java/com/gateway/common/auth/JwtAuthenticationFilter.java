package com.gateway.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Extracts and validates a JWT from the {@code Authorization: Bearer <token>} header.
 * <p>
 * On success a {@link GatewayAuthentication} is placed into the
 * {@link SecurityContextHolder}.  On any error the filter simply continues the
 * chain so that Spring Security's own entry-point handling can return a proper
 * 401 response.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            // No JWT present — allow chain to continue (anonymous access)
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);

            String userId = jwt.getSubject();
            String orgId = claimAsString(jwt, "org_id");
            String email = claimAsString(jwt, "email");
            List<String> roles = claimAsStringList(jwt, "roles");
            List<String> permissions = claimAsStringList(jwt, "permissions");
            String appId = claimAsString(jwt, "app_id");
            String scope = claimAsString(jwt, "scope");

            GatewayAuthentication authentication =
                    new GatewayAuthentication(userId, orgId, email, roles, permissions, appId, scope);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            // Let Spring Security handle the 401
        } catch (Exception ex) {
            log.warn("Unexpected error during JWT processing", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static String claimAsString(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> claimAsStringList(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        if (value instanceof String str) {
            // Some IdPs encode roles/permissions as a space-delimited string
            return str.isBlank() ? Collections.emptyList() : List.of(str.split("\\s+"));
        }
        return Collections.emptyList();
    }
}
