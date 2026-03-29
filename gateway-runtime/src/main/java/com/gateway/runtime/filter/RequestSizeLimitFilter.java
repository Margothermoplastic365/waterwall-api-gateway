package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.dto.ApiErrorResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Order(7) — Checks Content-Length against the configured maximum request
 * body size. Requests exceeding the limit receive a 413 Payload Too Large response.
 */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
public class RequestSizeLimitFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Value("${gateway.runtime.proxy.max-request-body-size-bytes:10485760}")
    private long maxRequestBodySizeBytes;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest request)
                || !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        long contentLength = request.getContentLengthLong();

        if (contentLength > maxRequestBodySizeBytes) {
            log.warn("Request body size {} exceeds maximum {} for {} {}",
                    contentLength, maxRequestBodySizeBytes,
                    request.getMethod(), request.getRequestURI());

            ApiErrorResponse errorBody = ApiErrorResponse.builder()
                    .status(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
                    .error("PAYLOAD_TOO_LARGE")
                    .errorCode("GW_413")
                    .message("Request body size " + contentLength
                            + " bytes exceeds maximum allowed "
                            + maxRequestBodySizeBytes + " bytes")
                    .path(request.getRequestURI())
                    .build();
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
