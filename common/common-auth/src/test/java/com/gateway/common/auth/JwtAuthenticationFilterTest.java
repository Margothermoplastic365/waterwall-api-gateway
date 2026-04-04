package com.gateway.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtDecoder);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWithValidJwt() throws ServletException, IOException {
        Jwt jwt = Jwt.withTokenValue("token123")
                .header("alg", "RS256")
                .subject("user1")
                .claim("org_id", "org1")
                .claim("email", "user@example.com")
                .claim("roles", List.of("admin", "user"))
                .claim("permissions", List.of("read", "write"))
                .claim("app_id", "app1")
                .claim("scope", "openid profile")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode("token123")).thenReturn(jwt);

        request.addHeader("Authorization", "Bearer token123");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        var auth = (GatewayAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getUserId()).isEqualTo("user1");
        assertThat(auth.getOrgId()).isEqualTo("org1");
        assertThat(auth.getEmail()).isEqualTo("user@example.com");
        assertThat(auth.getRoles()).containsExactly("admin", "user");
        assertThat(auth.getPermissions()).containsExactly("read", "write");
        assertThat(auth.getAppId()).isEqualTo("app1");
        assertThat(auth.getScope()).isEqualTo("openid profile");
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldSkipWhenNoAuthorizationHeader() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSkipWhenHeaderNotBearer() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSkipWhenEmptyToken() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer ");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldHandleJwtException() throws ServletException, IOException {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("Invalid token"));

        request.addHeader("Authorization", "Bearer bad-token");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldParseRolesFromList() throws ServletException, IOException {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user1")
                .claim("roles", List.of("editor", "viewer"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode("token")).thenReturn(jwt);

        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, response, filterChain);

        var auth = (GatewayAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getRoles()).containsExactly("editor", "viewer");
    }

    @Test
    void shouldParseRolesFromSpaceDelimitedString() throws ServletException, IOException {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user1")
                .claim("roles", "admin user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode("token")).thenReturn(jwt);

        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, response, filterChain);

        var auth = (GatewayAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getRoles()).containsExactly("admin", "user");
    }
}
