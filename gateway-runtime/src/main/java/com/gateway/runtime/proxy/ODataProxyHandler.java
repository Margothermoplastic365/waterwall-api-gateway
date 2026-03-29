package com.gateway.runtime.proxy;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol proxy handler for OData v4 requests.
 *
 * <p>Handles OData URL conventions ($filter, $select, $expand, $orderby, $top, $skip)
 * and forwards requests to upstream OData services. Caches $metadata responses
 * at the gateway level for performance.</p>
 */
@Component
@ConditionalOnProperty(name = "gateway.runtime.protocols.odata-enabled", havingValue = "true")
public class ODataProxyHandler implements ProtocolProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(ODataProxyHandler.class);

    private static final Set<String> ODATA_QUERY_OPTIONS = Set.of(
            "$filter", "$select", "$expand", "$orderby", "$top", "$skip",
            "$count", "$search", "$format", "$apply"
    );

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"
    );

    private final RestClient restClient;

    /** Cache for $metadata responses, keyed by upstream base URL */
    private final ConcurrentHashMap<String, CachedMetadata> metadataCache = new ConcurrentHashMap<>();

    public ODataProxyHandler(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String getProtocolType() {
        return "ODATA";
    }

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute) {
        long startTime = System.currentTimeMillis();
        GatewayRoute route = matchedRoute.getRoute();

        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        // Extract entity set name from the path
        String entitySetName = extractEntitySetName(requestUri, route.getPath());

        // Log OData query options used
        Map<String, String> odataOptions = extractODataQueryOptions(queryString);
        log.info("OData request: entitySet={}, method={}, queryOptions={}",
                entitySetName, request.getMethod(), odataOptions.keySet());

        // Check if this is a $metadata request
        if (requestUri.endsWith("/$metadata") || requestUri.endsWith("/$metadata/")) {
            return handleMetadataRequest(route, startTime);
        }

        // Forward to upstream OData service
        return forwardToUpstream(request, route, startTime);
    }

    private ResponseEntity<byte[]> handleMetadataRequest(GatewayRoute route, long startTime) {
        String upstreamBase = route.getUpstreamUrl();
        String cacheKey = upstreamBase;

        // Check cache
        CachedMetadata cached = metadataCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.debug("OData $metadata served from cache: upstream={}, latency={}ms",
                    upstreamBase, latencyMs);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .header("X-Gateway-Cache", "HIT")
                    .body(cached.body);
        }

        // Fetch from upstream
        String metadataUrl = upstreamBase.endsWith("/")
                ? upstreamBase + "$metadata"
                : upstreamBase + "/$metadata";

        try {
            ResponseEntity<byte[]> response = restClient
                    .method(HttpMethod.GET)
                    .uri(URI.create(metadataUrl))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Don't throw
                    })
                    .toEntity(byte[].class);

            long latencyMs = System.currentTimeMillis() - startTime;
            log.debug("OData $metadata fetched from upstream: status={}, latency={}ms",
                    response.getStatusCode(), latencyMs);

            // Cache successful responses
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                metadataCache.put(cacheKey, new CachedMetadata(response.getBody()));
            }

            return response;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("OData $metadata fetch failed: upstream={}, latency={}ms, error={}",
                    metadataUrl, latencyMs, e.getMessage());
            return ResponseEntity.status(502)
                    .body(("{\"error\":{\"code\":\"502\",\"message\":\"Failed to fetch OData metadata\"}}").getBytes());
        }
    }

    private ResponseEntity<byte[]> forwardToUpstream(HttpServletRequest request, GatewayRoute route,
                                                       long startTime) {
        String upstreamUrl = buildUpstreamUrl(request, route);

        try {
            HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());

            RestClient.RequestBodySpec requestSpec = restClient
                    .method(method)
                    .uri(URI.create(upstreamUrl))
                    .headers(headers -> copyRequestHeaders(request, headers));

            // Forward body for methods that carry a payload
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                byte[] body = request.getInputStream().readAllBytes();
                requestSpec.body(body);
            }

            ResponseEntity<byte[]> response = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Don't throw — forward upstream error as-is
                    })
                    .toEntity(byte[].class);

            long latencyMs = System.currentTimeMillis() - startTime;
            log.debug("OData upstream response: status={}, latency={}ms", response.getStatusCode(), latencyMs);

            return response;

        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("OData proxy read error: url={}, latency={}ms, error={}",
                    upstreamUrl, latencyMs, e.getMessage());
            return ResponseEntity.status(502)
                    .body(("{\"error\":{\"code\":\"502\",\"message\":\"Failed to read request body\"}}").getBytes());
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("OData proxy error: url={}, latency={}ms, error={}",
                    upstreamUrl, latencyMs, e.getMessage());
            return ResponseEntity.status(502)
                    .body(("{\"error\":{\"code\":\"502\",\"message\":\"Error communicating with upstream OData service\"}}").getBytes());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String buildUpstreamUrl(HttpServletRequest request, GatewayRoute route) {
        String requestPath = request.getRequestURI();
        String routePath = route.getPath();
        String remainingPath;

        if (route.isStripPrefix() && routePath != null) {
            String staticPrefix = routePath.replaceAll("/\\*\\*$", "")
                    .replaceAll("/\\{[^}]+}.*", "");
            if (!staticPrefix.isEmpty() && requestPath.startsWith(staticPrefix)) {
                remainingPath = requestPath.substring(staticPrefix.length());
            } else {
                remainingPath = requestPath;
            }
        } else {
            remainingPath = requestPath;
        }

        if (!remainingPath.isEmpty() && !remainingPath.startsWith("/")) {
            remainingPath = "/" + remainingPath;
        }

        String upstreamBase = route.getUpstreamUrl();
        if (upstreamBase.endsWith("/")) {
            upstreamBase = upstreamBase.substring(0, upstreamBase.length() - 1);
        }

        String fullUrl = upstreamBase + remainingPath;

        // Append query string (includes OData query options)
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            fullUrl = fullUrl + "?" + queryString;
        }

        return fullUrl;
    }

    private String extractEntitySetName(String requestUri, String routePath) {
        String path = requestUri;
        if (routePath != null) {
            String prefix = routePath.replaceAll("/\\*\\*$", "").replaceAll("/\\{[^}]+}.*", "");
            if (!prefix.isEmpty() && path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            }
        }
        // Remove leading slash and take the first segment
        path = path.replaceFirst("^/+", "");
        int slashIdx = path.indexOf('/');
        if (slashIdx > 0) {
            path = path.substring(0, slashIdx);
        }
        // Remove query params or parens (e.g., "Products(1)")
        int parenIdx = path.indexOf('(');
        if (parenIdx > 0) {
            path = path.substring(0, parenIdx);
        }
        return path.isEmpty() ? "root" : path;
    }

    private Map<String, String> extractODataQueryOptions(String queryString) {
        Map<String, String> options = new LinkedHashMap<>();
        if (queryString == null || queryString.isBlank()) {
            return options;
        }
        for (String param : queryString.split("&")) {
            String[] parts = param.split("=", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            if (ODATA_QUERY_OPTIONS.contains(key)) {
                options.put(key, value);
            }
        }
        return options;
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders targetHeaders) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                targetHeaders.add(headerName, values.nextElement());
            }
        }
    }

    /**
     * Simple cache entry for OData $metadata responses.
     * Expires after 5 minutes.
     */
    private static class CachedMetadata {
        final byte[] body;
        final long cachedAt;
        static final long TTL_MS = 5 * 60 * 1000L; // 5 minutes

        CachedMetadata(byte[] body) {
            this.body = body;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > TTL_MS;
        }
    }
}
