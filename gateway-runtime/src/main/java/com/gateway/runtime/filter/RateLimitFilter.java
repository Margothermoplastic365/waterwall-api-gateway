package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.cache.RateLimitCounter;
import com.gateway.common.dto.ApiErrorResponse;
import com.gateway.common.events.EventPublisher;
import com.gateway.runtime.model.GatewayPlan;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.StrictRateLimitService;
import com.gateway.runtime.service.TokenBucketRateLimiter;
import com.gateway.runtime.service.TokenBucketRateLimiter.ConsumeResult;
import com.gateway.runtime.service.TokenBucketRateLimiter.Window;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Order(50) — Enforces rate limits based on the subscription plan.
 * Supports two enforcement modes:
 * <ul>
 *   <li><b>SOFT</b> — Uses a token bucket algorithm via {@link TokenBucketRateLimiter} supporting
 *       multiple time windows (per-second, per-minute, per-day) with burst allowance.
 *       Falls back to the legacy {@link RateLimitCounter} for async MQ sync across nodes
 *       when only per-minute limits are configured.</li>
 *   <li><b>STRICT</b> — Uses a database-backed atomic counter via {@link StrictRateLimitService}
 *       with per-minute fixed-window semantics (backward compatible).</li>
 * </ul>
 * Sets standard rate limit response headers (X-RateLimit-Limit, X-RateLimit-Remaining,
 * X-RateLimit-Reset) reflecting the most restrictive window on every request.
 */
@Slf4j
@Component
@Order(50)
public class RateLimitFilter implements Filter {

    private final RateLimitCounter rateLimitCounter;
    private final EventPublisher eventPublisher;
    private final StrictRateLimitService strictRateLimitService;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    public RateLimitFilter(RateLimitCounter rateLimitCounter,
                           EventPublisher eventPublisher,
                           StrictRateLimitService strictRateLimitService,
                           TokenBucketRateLimiter tokenBucketRateLimiter,
                           ObjectMapper objectMapper) {
        this.rateLimitCounter = rateLimitCounter;
        this.eventPublisher = eventPublisher;
        this.strictRateLimitService = strictRateLimitService;
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
        this.objectMapper = objectMapper;
        this.nodeId = resolveNodeId();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        GatewayPlan plan = (GatewayPlan) request.getAttribute(SubscriptionCheckFilter.ATTR_PLAN);

        // If no plan or no rate limit configured at all, pass through
        if (plan == null || !hasAnyRateLimit(plan)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        GatewayAuthentication authentication = getAuthentication();
        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);

        String appId = authentication != null && authentication.getAppId() != null ? authentication.getAppId() : "anonymous";
        String apiId = matchedRoute != null ? matchedRoute.getRoute().getApiId().toString() : "unknown";

        if ("STRICT".equals(plan.getEnforcement())) {
            handleStrictMode(request, response, filterChain, plan, appId, apiId);
        } else {
            handleSoftMode(request, response, filterChain, plan, appId, apiId);
        }
    }

    /**
     * STRICT enforcement: database-backed atomic counter with per-minute fixed window.
     * Maintains full backward compatibility with the existing implementation.
     */
    private void handleStrictMode(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain, GatewayPlan plan,
                                  String appId, String apiId) throws IOException, ServletException {

        Integer rpmLimit = plan.getRequestsPerMinute();
        if (rpmLimit == null || rpmLimit <= 0) {
            // STRICT mode only supports per-minute; if not configured, pass through
            filterChain.doFilter(request, response);
            return;
        }

        int limit = rpmLimit;
        int windowSeconds = 60;
        long currentWindow = Instant.now().getEpochSecond() / windowSeconds;
        long windowResetEpoch = (currentWindow + 1) * windowSeconds;

        String rateLimitKey = "ratelimit:" + appId + ":" + apiId + ":" + currentWindow;

        int currentCount = strictRateLimitService.incrementAndCheck(rateLimitKey, windowSeconds, limit);
        boolean exceeded = (currentCount < 0);

        if (exceeded) {
            log.debug("Rate limit exceeded (STRICT): key={}, limit={}", rateLimitKey, limit);
            setRateLimitHeaders(response, limit, 0, windowResetEpoch);
            writeErrorResponse(response, request, limit, windowResetEpoch);
            return;
        }

        int remaining = limit - currentCount;
        setRateLimitHeaders(response, limit, remaining, windowResetEpoch);
        filterChain.doFilter(request, response);
    }

    /**
     * SOFT enforcement: token bucket algorithm supporting multiple time windows
     * (per-second, per-minute, per-day) with burst allowance from the plan.
     * All configured windows are checked; the request is denied if any window is exhausted.
     * Response headers reflect the most restrictive window.
     */
    private void handleSoftMode(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain, GatewayPlan plan,
                                String appId, String apiId) throws IOException, ServletException {

        Map<Window, Integer> windows = buildWindowMap(plan);

        if (windows.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumeResult result = tokenBucketRateLimiter.tryConsumeAllWindows(
                appId, apiId, windows, plan.getBurstAllowance());

        if (!result.allowed()) {
            log.debug("Rate limit exceeded (SOFT/token-bucket): appId={}, apiId={}, limit={}",
                    appId, apiId, result.limit());
            setRateLimitHeaders(response, result.limit(), 0, result.resetEpochSeconds());
            writeErrorResponse(response, request, result.limit(), result.resetEpochSeconds());
            return;
        }

        setRateLimitHeaders(response, result.limit(), result.remaining(), result.resetEpochSeconds());

        // Async broadcast for cross-node awareness (best-effort, non-blocking)
        try {
            String syncKey = "ratelimit:" + appId + ":" + apiId;
            eventPublisher.publishRateLimitSync(syncKey, 1, nodeId);
        } catch (Exception e) {
            log.debug("Failed to publish rate limit sync event: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Builds the map of active rate-limit windows from the plan configuration.
     * Only windows with a positive limit are included.
     */
    private Map<Window, Integer> buildWindowMap(GatewayPlan plan) {
        Map<Window, Integer> windows = new LinkedHashMap<>();

        if (plan.getRequestsPerSecond() != null && plan.getRequestsPerSecond() > 0) {
            windows.put(Window.PER_SECOND, plan.getRequestsPerSecond());
        }
        if (plan.getRequestsPerMinute() != null && plan.getRequestsPerMinute() > 0) {
            windows.put(Window.PER_MINUTE, plan.getRequestsPerMinute());
        }
        if (plan.getRequestsPerDay() != null && plan.getRequestsPerDay() > 0) {
            windows.put(Window.PER_DAY, plan.getRequestsPerDay());
        }

        return windows;
    }

    /**
     * Returns true if the plan has at least one rate limit configured.
     */
    private boolean hasAnyRateLimit(GatewayPlan plan) {
        return (plan.getRequestsPerSecond() != null && plan.getRequestsPerSecond() > 0)
                || (plan.getRequestsPerMinute() != null && plan.getRequestsPerMinute() > 0)
                || (plan.getRequestsPerDay() != null && plan.getRequestsPerDay() > 0);
    }

    private void setRateLimitHeaders(HttpServletResponse response, int limit, int remaining, long resetEpoch) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpoch));
    }

    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    int limit, long resetEpoch) throws IOException {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(429)
                .error("RATE_LIMIT_EXCEEDED")
                .errorCode("GW_429")
                .message("Rate limit of " + limit + " requests exceeded. Retry after " + Instant.ofEpochSecond(resetEpoch))
                .path(request.getRequestURI())
                .build();
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private GatewayAuthentication getAuthentication() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof GatewayAuthentication gatewayAuth) {
            return gatewayAuth;
        }
        return null;
    }

    private static String resolveNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + ProcessHandle.current().pid();
        }
    }
}
