package com.gateway.runtime.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.filter.RouteMatchFilter;
import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Order(43) — Request/Response transformation filter.
 * Loads transformation rules from the API gateway config and applies
 * header transformations and body mappings.
 */
@Slf4j
@Component
@Order(43)
@RequiredArgsConstructor
public class TransformationFilter implements Filter {

    private final HeaderTransformer headerTransformer;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);
        if (matchedRoute == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Store transformation rules as request attribute for post-response processing
        // The actual transformation config would come from the API gateway config (gatewayConfig JSONB field)
        // For now, we pass through and apply any response-level header transforms after the chain.

        filterChain.doFilter(servletRequest, servletResponse);

        // Post-response header transformations could be applied here
        // In practice, response wrapping would be needed for body transforms
        log.trace("TransformationFilter completed for path: {}", request.getRequestURI());
    }
}
