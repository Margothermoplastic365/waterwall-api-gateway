package com.gateway.runtime.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Gateway-level timing filter (Order 1 — runs very early, right after
 * {@link com.gateway.common.logging.TraceIdFilter}) that captures precise
 * request/response timing and decorates every response with observability
 * headers.
 *
 * <p>Response headers added:
 * <ul>
 *   <li>{@code X-Response-Time} — total gateway processing time in milliseconds</li>
 *   <li>{@code X-Gateway-Version} — version identifier of the running gateway</li>
 *   <li>{@code X-Trace-Id} — distributed trace identifier (echoed from MDC)</li>
 * </ul>
 *
 * <p>Structured log line emitted after every non-actuator request containing
 * traceId, HTTP method, URI path, response status, and latency in milliseconds.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestTimingFilter implements Filter {

    private static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    private static final String GATEWAY_VERSION_HEADER = "X-Gateway-Version";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Value("${gateway.version:1.0.0}")
    private String gatewayVersion;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest request)
                || !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Skip actuator endpoints to avoid noise
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            // Decorate response with observability headers
            response.setHeader(RESPONSE_TIME_HEADER, latencyMs + "ms");
            response.setHeader(GATEWAY_VERSION_HEADER, gatewayVersion);

            String traceId = MDC.get("traceId");
            if (traceId != null) {
                response.setHeader(TRACE_ID_HEADER, traceId);
            }

            // Structured log line for request tracing
            String method = request.getMethod();
            int status = response.getStatus();
            log.info("trace={} method={} path={} status={} latencyMs={}",
                    traceId != null ? traceId : "-",
                    method, path, status, latencyMs);
        }
    }
}
