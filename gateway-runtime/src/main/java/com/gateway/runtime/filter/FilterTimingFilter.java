package com.gateway.runtime.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Order(1) — First filter in the chain. Measures total filter chain processing time.
 * Logs a warning when total processing exceeds the slow threshold.
 *
 * Also adds response headers showing timing breakdown for debugging:
 * - X-Gateway-Time: total processing time in ms
 */
@Slf4j
@Component
@Order(1)
public class FilterTimingFilter implements Filter {

    private static final long SLOW_THRESHOLD_MS = 100;
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong slowRequests = new AtomicLong();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        long start = System.nanoTime();
        request.setAttribute("gateway.filterChainStartNanos", start);

        try {
            chain.doFilter(servletRequest, servletResponse);
        } finally {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            totalRequests.incrementAndGet();

            response.setHeader("X-Gateway-Time", elapsed + "ms");

            if (elapsed > SLOW_THRESHOLD_MS) {
                slowRequests.incrementAndGet();
                log.warn("SLOW REQUEST: {} {} took {}ms (status={}) — slow={}/total={}",
                        request.getMethod(), request.getRequestURI(), elapsed,
                        response.getStatus(), slowRequests.get(), totalRequests.get());
            }
        }
    }
}
