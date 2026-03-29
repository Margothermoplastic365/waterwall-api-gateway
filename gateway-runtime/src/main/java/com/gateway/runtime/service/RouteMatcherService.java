package com.gateway.runtime.service;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Matches incoming HTTP requests to cached gateway routes.
 *
 * <p>Matching priority (highest first):
 * <ol>
 *   <li>Exact match: {@code /v1/orders} == {@code /v1/orders}</li>
 *   <li>Path-variable match: {@code /v1/orders/{id}} matches {@code /v1/orders/123}</li>
 *   <li>Prefix (glob) match: {@code /v1/orders/**} matches {@code /v1/orders/123/items}</li>
 * </ol>
 *
 * <p>When multiple routes match, the one with the highest {@code priority} value wins.
 * If priorities are equal, more specific matches (exact &gt; path-variable &gt; prefix) win.</p>
 */
@Service
public class RouteMatcherService {

    private static final Logger log = LoggerFactory.getLogger(RouteMatcherService.class);

    private static final String GLOB_SUFFIX = "/**";

    private final RouteConfigService routeConfigService;

    public RouteMatcherService(RouteConfigService routeConfigService) {
        this.routeConfigService = routeConfigService;
    }

    /**
     * Find the best matching route for the given request path and HTTP method.
     *
     * @param requestPath   the incoming request path (e.g. {@code /v1/orders/123/items})
     * @param requestMethod the HTTP method (e.g. {@code GET})
     * @return the matched route with extracted path variables, or {@code null} if no match
     */
    public MatchedRoute match(String requestPath, String requestMethod) {
        List<GatewayRoute> allRoutes = routeConfigService.getAllRoutes();

        MatchedRoute bestMatch = null;
        int bestPriority = Integer.MIN_VALUE;
        int bestSpecificity = -1; // 3 = exact, 2 = path-variable, 1 = prefix

        for (GatewayRoute route : allRoutes) {
            if (!route.isEnabled()) {
                continue;
            }

            // Check HTTP method: null/empty means any method
            if (route.getMethod() != null && !route.getMethod().isEmpty()
                    && !route.getMethod().equalsIgnoreCase(requestMethod)) {
                continue;
            }

            String routePath = route.getPath();
            Map<String, String> pathVars = new LinkedHashMap<>();
            int specificity;

            if (routePath.endsWith(GLOB_SUFFIX)) {
                // Prefix (glob) match: /v1/orders/** matches /v1/orders, /v1/orders/123, etc.
                String prefix = routePath.substring(0, routePath.length() - GLOB_SUFFIX.length());
                if (requestPath.equals(prefix) || requestPath.startsWith(prefix + "/")) {
                    specificity = 1;
                } else {
                    continue;
                }
            } else if (routePath.contains("{")) {
                // Path-variable match: /v1/orders/{id} matches /v1/orders/123
                Map<String, String> extracted = matchPathVariables(routePath, requestPath);
                if (extracted == null) {
                    continue;
                }
                pathVars = extracted;
                specificity = 2;
            } else {
                // Exact match
                if (!requestPath.equals(routePath)) {
                    continue;
                }
                specificity = 3;
            }

            // Determine if this match is better than the current best
            boolean isBetter = false;
            if (route.getPriority() > bestPriority) {
                isBetter = true;
            } else if (route.getPriority() == bestPriority && specificity > bestSpecificity) {
                isBetter = true;
            }

            if (isBetter) {
                bestMatch = MatchedRoute.builder()
                        .route(route)
                        .pathVariables(pathVars)
                        .build();
                bestPriority = route.getPriority();
                bestSpecificity = specificity;
            }
        }

        if (bestMatch != null) {
            log.debug("Matched route: {} {} -> {} (priority={})",
                    bestMatch.getRoute().getMethod(),
                    bestMatch.getRoute().getPath(),
                    bestMatch.getRoute().getUpstreamUrl(),
                    bestMatch.getRoute().getPriority());
        }

        return bestMatch;
    }

    /**
     * Build the upstream URL for the matched route. If {@code stripPrefix} is true,
     * the route path prefix is removed from the request path before appending it
     * to the upstream URL.
     *
     * @param matchedRoute  the matched route
     * @param requestPath   the original incoming request path
     * @return the fully resolved upstream URL
     */
    public String buildUpstreamUrl(MatchedRoute matchedRoute, String requestPath) {
        GatewayRoute route = matchedRoute.getRoute();
        String upstreamBase = route.getUpstreamUrl();

        if (!route.isStripPrefix()) {
            // Forward the full request path to the upstream
            return appendPath(upstreamBase, requestPath);
        }

        // Strip the route's path prefix from the request path
        String routePath = route.getPath();

        // Remove glob suffix for prefix routes
        if (routePath.endsWith(GLOB_SUFFIX)) {
            routePath = routePath.substring(0, routePath.length() - GLOB_SUFFIX.length());
        }

        // For path-variable routes, build the prefix from static segments
        // e.g., /v1/orders/{id} with request /v1/orders/123 -> strip /v1/orders/{id} equivalent
        if (routePath.contains("{")) {
            routePath = buildStaticPrefix(routePath, requestPath);
        }

        String strippedPath;
        if (requestPath.startsWith(routePath)) {
            strippedPath = requestPath.substring(routePath.length());
        } else {
            strippedPath = requestPath;
        }

        return appendPath(upstreamBase, strippedPath);
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Attempt to match a route pattern with path variables against the request path.
     *
     * @return map of variable name to value, or {@code null} if no match
     */
    private Map<String, String> matchPathVariables(String routePattern, String requestPath) {
        String[] patternSegments = splitPath(routePattern);
        String[] requestSegments = splitPath(requestPath);

        if (patternSegments.length != requestSegments.length) {
            return null;
        }

        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < patternSegments.length; i++) {
            String pattern = patternSegments[i];
            String actual = requestSegments[i];

            if (pattern.startsWith("{") && pattern.endsWith("}")) {
                String varName = pattern.substring(1, pattern.length() - 1);
                vars.put(varName, actual);
            } else if (!pattern.equals(actual)) {
                return null;
            }
        }
        return vars;
    }

    /**
     * Build the matched prefix portion of the request path for a path-variable route,
     * so that we can strip it correctly. For example, given pattern {@code /v1/orders/{id}}
     * and request {@code /v1/orders/123}, this returns {@code /v1/orders/123}.
     */
    private String buildStaticPrefix(String routePattern, String requestPath) {
        String[] patternSegments = splitPath(routePattern);
        String[] requestSegments = splitPath(requestPath);

        int len = Math.min(patternSegments.length, requestSegments.length);
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < len; i++) {
            prefix.append('/').append(requestSegments[i]);
        }
        return prefix.toString();
    }

    private String[] splitPath(String path) {
        if (path == null || path.isEmpty()) {
            return new String[0];
        }
        // Remove leading slash, then split
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("/");
    }

    private String appendPath(String base, String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return base;
        }
        boolean baseEndsWithSlash = base.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return base + path.substring(1);
        } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return base + "/" + path;
        } else {
            return base + path;
        }
    }
}
