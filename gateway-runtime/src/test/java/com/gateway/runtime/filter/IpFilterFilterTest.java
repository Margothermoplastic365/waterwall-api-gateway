package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class IpFilterFilterTest {

    @Mock
    private FilterChain filterChain;

    private IpFilterFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new IpFilterFilter(new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        ReflectionTestUtils.setField(filter, "ipWhitelistConfig", "");
        ReflectionTestUtils.setField(filter, "ipBlacklistConfig", "");
        ReflectionTestUtils.setField(filter, "trustXForwardedFor", true);

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.setRemoteAddr("192.168.1.100");
        response = new MockHttpServletResponse();
    }

    private void initFilter() {
        filter.init();
    }

    @Test
    void shouldPassWhenNoListsConfigured() throws Exception {
        initFilter();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldBlockBlacklistedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "ipBlacklistConfig", "192.168.1.0/24");
        initFilter();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldAllowWhitelistedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "ipWhitelistConfig", "192.168.1.0/24");
        initFilter();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldBlockNonWhitelistedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "ipWhitelistConfig", "10.0.0.0/8");
        initFilter();

        request.setRemoteAddr("192.168.1.100");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldUseXForwardedForWhenTrusted() throws Exception {
        ReflectionTestUtils.setField(filter, "ipBlacklistConfig", "10.10.10.10");
        initFilter();

        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "10.10.10.10, 192.168.1.1");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldSkipActuatorEndpoints() throws Exception {
        ReflectionTestUtils.setField(filter, "ipBlacklistConfig", "192.168.1.0/24");
        initFilter();

        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
