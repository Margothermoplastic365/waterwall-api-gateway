package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.service.RouteConfigService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

    @Mock
    private RouteConfigService routeConfigService;

    @Mock
    private FilterChain filterChain;

    private SecurityHeadersFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter(routeConfigService, new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        ReflectionTestUtils.setField(filter, "hstsMaxAge", 31536000L);
        ReflectionTestUtils.setField(filter, "frameOptions", "DENY");
        ReflectionTestUtils.setField(filter, "contentSecurityPolicy", "default-src 'self'");
        ReflectionTestUtils.setField(filter, "referrerPolicy", "strict-origin-when-cross-origin");
        ReflectionTestUtils.setField(filter, "permissionsPolicy", "geolocation=()");
        ReflectionTestUtils.setField(filter, "defaultCorsOrigin", "*");

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldSetAllSecurityHeaders() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
        assertThat(response.getHeader("Permissions-Policy")).isEqualTo("geolocation=()");
    }

    @Test
    void shouldSetDefaultCorsOrigin() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
    }

    @Test
    void shouldCallFilterChain() throws Exception {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
