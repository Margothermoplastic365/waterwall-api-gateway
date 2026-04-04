package com.gateway.runtime.filter;

import com.gateway.runtime.service.AccessLogService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessLogFilterTest {

    @Mock
    private AccessLogService accessLogService;

    @Mock
    private FilterChain filterChain;

    private AccessLogFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new AccessLogFilter(accessLogService);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldCallAccessLogService() throws Exception {
        response.setStatus(200);

        filter.doFilter(request, response, filterChain);

        verify(accessLogService).logRequest(
                eq(request), eq(200),
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                isNull(), isNull());
    }

    @Test
    void shouldSkipActuatorEndpoints() throws Exception {
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(accessLogService, never()).logRequest(
                any(), anyInt(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), any(), any());
    }

    @Test
    void shouldHandleMissingUpstreamLatencyAttribute() throws Exception {
        // No "gateway.upstreamLatencyMs" attribute set on request

        filter.doFilter(request, response, filterChain);

        verify(accessLogService).logRequest(
                eq(request), anyInt(),
                anyLong(), eq(0L), anyLong(), anyLong(), anyLong(),
                isNull(), isNull());
    }

    @Test
    void shouldAlwaysCallFilterChain() throws Exception {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), any(ContentCachingResponseWrapper.class));
    }
}
