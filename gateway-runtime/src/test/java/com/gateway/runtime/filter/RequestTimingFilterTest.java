package com.gateway.runtime.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestTimingFilterTest {

    @InjectMocks
    private RequestTimingFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "gatewayVersion", "2.5.0");
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @Test
    void shouldSetResponseTimeHeader() throws Exception {
        filter.doFilter(request, response, filterChain);

        String header = response.getHeader("X-Response-Time");
        assertThat(header).isNotNull();
        assertThat(header).matches("\\d+ms");
    }

    @Test
    void shouldSetGatewayVersionHeader() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Gateway-Version")).isEqualTo("2.5.0");
    }

    @Test
    void shouldSetTraceIdFromMdc() throws Exception {
        MDC.put("traceId", "abc-123-trace");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("abc-123-trace");
    }

    @Test
    void shouldSkipActuatorEndpoints() throws Exception {
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-Response-Time")).isNull();
        assertThat(response.getHeader("X-Gateway-Version")).isNull();
        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }

    @Test
    void shouldCallFilterChain() throws Exception {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
