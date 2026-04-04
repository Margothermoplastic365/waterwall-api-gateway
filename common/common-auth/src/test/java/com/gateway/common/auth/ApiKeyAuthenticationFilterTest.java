package com.gateway.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new ApiKeyAuthenticationFilter(cacheManager);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipWhenAuthenticationAlreadySet() throws ServletException, IOException {
        GatewayAuthentication existingAuth = new GatewayAuthentication(
                "user1", "org1", null, List.of(), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        request.addHeader("X-API-Key", "some-key");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(cacheManager);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existingAuth);
    }

    @Test
    void shouldAuthenticateWithApiKeyFromHeader() throws ServletException, IOException {
        String apiKey = "test-api-key-123";
        Map<String, Object> appInfo = Map.of(
                "appId", "app-42",
                "orgId", "org-7",
                "permissions", List.of("read", "write"));

        when(cacheManager.getCache("apiKeys")).thenReturn(cache);
        when(cache.get(any(String.class), eq(Map.class))).thenReturn(appInfo);

        request.addHeader("X-API-Key", apiKey);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        var auth = (GatewayAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAppId()).isEqualTo("app-42");
        assertThat(auth.getOrgId()).isEqualTo("org-7");
        assertThat(auth.getPermissions()).containsExactly("read", "write");
        assertThat(auth.getPrincipal()).isNull(); // no userId for API key auth
    }

    @Test
    void shouldAuthenticateWithApiKeyFromQueryParam() throws ServletException, IOException {
        String apiKey = "query-param-key";
        Map<String, Object> appInfo = Map.of(
                "appId", "app-99",
                "orgId", "org-5",
                "permissions", List.of("execute"));

        when(cacheManager.getCache("apiKeys")).thenReturn(cache);
        when(cache.get(any(String.class), eq(Map.class))).thenReturn(appInfo);

        request.setParameter("apikey", apiKey);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        var auth = (GatewayAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAppId()).isEqualTo("app-99");
        assertThat(auth.getOrgId()).isEqualTo("org-5");
        assertThat(auth.getPermissions()).containsExactly("execute");
    }

    @Test
    void shouldContinueWhenNoApiKey() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueWhenKeyNotInCache() throws ServletException, IOException {
        when(cacheManager.getCache("apiKeys")).thenReturn(cache);
        when(cache.get(any(String.class), eq(Map.class))).thenReturn(null);

        request.addHeader("X-API-Key", "unknown-key");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueWhenCacheIsNull() throws ServletException, IOException {
        when(cacheManager.getCache("apiKeys")).thenReturn(null);

        request.addHeader("X-API-Key", "some-key");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldHandleExceptionGracefully() throws ServletException, IOException {
        when(cacheManager.getCache("apiKeys")).thenThrow(new RuntimeException("Cache failure"));

        request.addHeader("X-API-Key", "some-key");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
