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
import static org.mockito.Mockito.*;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RequestSizeLimitFilterTest {

    @Mock
    private FilterChain filterChain;

    private RequestSizeLimitFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestSizeLimitFilter(new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        ReflectionTestUtils.setField(filter, "maxRequestBodySizeBytes", 10_485_760L);

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldPassWhenContentLengthUnderLimit() throws Exception {
        request.addHeader("Content-Length", "1024");
        request.setContentType("application/json");
        request.setContent(new byte[1024]);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn413WhenContentLengthExceedsLimit() throws Exception {
        // MockHttpServletRequest.getContentLengthLong() derives from actual content,
        // so we need to set the content to be oversized or use a mock request.
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getContentLengthLong()).thenReturn(10_485_761L);
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(mockRequest, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(filterChain, never()).doFilter(mockRequest, response);

        String body = response.getContentAsString();
        assertThat(body).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void shouldPassWhenNoContentLengthHeader() throws Exception {
        // No Content-Length header → getContentLengthLong returns -1

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAlwaysCallFilterChainWhenUnderLimit() throws Exception {
        request.addHeader("Content-Length", "500");
        request.setContent(new byte[500]);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
