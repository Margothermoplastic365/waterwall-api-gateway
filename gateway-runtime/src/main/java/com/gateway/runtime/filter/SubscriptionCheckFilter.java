package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.dto.ApiErrorResponse;
import com.gateway.runtime.model.GatewayPlan;
import com.gateway.runtime.model.GatewaySubscription;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.RouteConfigService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Order(40) — Verifies that the authenticated application has an active subscription
 * to the API being accessed. Stores the subscription and plan details as request
 * attributes for downstream filters (e.g., rate limiting).
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class SubscriptionCheckFilter implements Filter {

    public static final String ATTR_SUBSCRIPTION = "gateway.subscription";
    public static final String ATTR_PLAN = "gateway.plan";

    private final RouteConfigService routeConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Anonymous requests that passed route auth check don't need subscription
        String authType = (String) request.getAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE);
        if (GatewayAuthFilter.AUTH_TYPE_ANONYMOUS.equals(authType)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        GatewayAuthentication authentication = getAuthentication();
        if (authentication == null) {
            log.warn("SubscriptionCheckFilter: no authentication found in SecurityContext");
            writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "GW_401", "Authentication required");
            return;
        }

        String appIdStr = authentication.getAppId();
        if (appIdStr == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        UUID appId = UUID.fromString(appIdStr);

        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);
        if (matchedRoute == null) {
            log.error("SubscriptionCheckFilter: no matched route found in request attributes");
            writeErrorResponse(response, request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "GW_500", "Internal routing error");
            return;
        }

        UUID apiId = matchedRoute.getRoute().getApiId();
        String environment = (String) request.getAttribute(GatewayAuthFilter.ATTR_ENVIRONMENT);

        java.util.Optional<GatewaySubscription> subOpt = routeConfigService.getSubscription(appId, apiId, environment);
        if (subOpt.isEmpty()) {
            log.debug("No active subscription for appId={} apiId={} env={}", appId, apiId, environment);
            writeErrorResponse(response, request, HttpServletResponse.SC_FORBIDDEN,
                    "SUBSCRIPTION_REQUIRED", "GW_403",
                    "An active subscription is required to access this API");
            return;
        }

        GatewaySubscription subscription = subOpt.get();
        request.setAttribute(ATTR_SUBSCRIPTION, subscription);

        Map<UUID, GatewayPlan> plans = routeConfigService.getPlansById();
        GatewayPlan plan = plans.get(subscription.getPlanId());
        if (plan != null) {
            request.setAttribute(ATTR_PLAN, plan);
        }

        log.debug("Subscription verified: appId={}, apiId={}, planId={}",
                appId, apiId, subscription.getPlanId());

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private GatewayAuthentication getAuthentication() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof GatewayAuthentication gatewayAuth) {
            return gatewayAuth;
        }
        return null;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    int status, String error, String errorCode,
                                    String message) throws IOException {
        ApiErrorResponse errorBody = ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .build();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
