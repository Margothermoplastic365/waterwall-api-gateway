package com.gateway.runtime.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.cache.CacheNames;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.filter.RouteMatchFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Order(45) — Response caching filter that sits after subscription check and before rate limiting.
 * Only caches GET requests with 200 status. Respects Cache-Control: no-cache and X-Cache-Bypass headers.
 * Supports ETag / conditional requests (If-None-Match, If-Modified-Since).
 * Protocol-aware: adjusts cache key for GraphQL/SOAP and skips caching for gRPC/WebSocket/SSE.
 */
@Slf4j
@Component
@Order(45)
@RequiredArgsConstructor
public class ResponseCacheFilter implements Filter {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Determine protocol type from matched route
        String protocolType = resolveProtocolType(request);

        // Skip caching for non-cacheable protocols
        if (isNonCacheableProtocol(protocolType)) {
            log.debug("Skipping cache for non-cacheable protocol: {}", protocolType);
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Only cache GET requests (REST/default). For GraphQL/SOAP allow POST as well.
        boolean isGraphqlOrSoap = "GRAPHQL".equalsIgnoreCase(protocolType)
                || "SOAP".equalsIgnoreCase(protocolType);
        if (!"GET".equalsIgnoreCase(request.getMethod()) && !isGraphqlOrSoap) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Respect Cache-Control: no-cache
        String cacheControl = request.getHeader("Cache-Control");
        if (cacheControl != null && cacheControl.contains("no-cache")) {
            response.setHeader("X-Cache", "BYPASS");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Respect X-Cache-Bypass: true
        String cacheBypass = request.getHeader("X-Cache-Bypass");
        if ("true".equalsIgnoreCase(cacheBypass)) {
            response.setHeader("X-Cache", "BYPASS");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Wrap request if we need to read the body for GraphQL/SOAP cache key
        HttpServletRequest effectiveRequest = request;
        if (isGraphqlOrSoap) {
            effectiveRequest = new ContentCachingRequestWrapper(request);
        }

        // Build cache key (protocol-aware)
        String cacheKey = buildCacheKey(effectiveRequest, protocolType);

        // Check cache for hit
        Cache cache = cacheManager.getCache(CacheNames.API_RESPONSES);
        if (cache != null) {
            CachedResponse cached = cache.get(cacheKey, CachedResponse.class);
            if (cached != null) {
                log.debug("Cache HIT for key: {}", cacheKey);

                // Check If-None-Match (ETag conditional request)
                String ifNoneMatch = request.getHeader("If-None-Match");
                if (ifNoneMatch != null && cached.getEtag() != null
                        && ifNoneMatch.equals(cached.getEtag())) {
                    log.debug("ETag match — returning 304 Not Modified");
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", cached.getEtag());
                    response.setHeader("X-Cache", "HIT");
                    return;
                }

                // Check If-Modified-Since
                String ifModifiedSince = request.getHeader("If-Modified-Since");
                if (ifModifiedSince != null && cached.getCachedAt() != null) {
                    try {
                        ZonedDateTime clientDate = ZonedDateTime.parse(ifModifiedSince, HTTP_DATE_FORMATTER);
                        Instant clientInstant = clientDate.toInstant();
                        // If cached timestamp is before or equal to If-Modified-Since → 304
                        if (!cached.getCachedAt().isAfter(clientInstant)) {
                            log.debug("Not modified since {} — returning 304", ifModifiedSince);
                            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            response.setHeader("X-Cache", "HIT");
                            if (cached.getEtag() != null) {
                                response.setHeader("ETag", cached.getEtag());
                            }
                            return;
                        }
                    } catch (DateTimeParseException e) {
                        log.debug("Could not parse If-Modified-Since header: {}", ifModifiedSince);
                    }
                }

                writeCachedResponse(response, cached);
                return;
            }
        }

        // Cache MISS — continue chain with a wrapper to capture response
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, responseWrapper);

        // Only cache 200 responses
        if (responseWrapper.getStatus() == 200 && cache != null) {
            byte[] body = responseWrapper.getContentAsByteArray();
            Map<String, String> headers = extractResponseHeaders(responseWrapper);

            // Compute ETag from body as MD5 hex
            String etag = computeEtag(body);
            Instant cachedAt = Instant.now();

            CachedResponse cachedResponse = CachedResponse.builder()
                    .statusCode(200)
                    .headers(headers)
                    .body(body)
                    .cachedAt(cachedAt)
                    .etag(etag)
                    .build();

            cache.put(cacheKey, cachedResponse);
            log.debug("Cache MISS — cached response for key: {}", cacheKey);

            // Set ETag and Last-Modified headers on the outgoing response
            responseWrapper.setHeader("ETag", etag);
            responseWrapper.setHeader("Last-Modified", formatHttpDate(cachedAt));
        }

        responseWrapper.setHeader("X-Cache", "MISS");
        responseWrapper.copyBodyToResponse();
    }

    /**
     * Resolve the protocol type from the matched route attribute.
     */
    private String resolveProtocolType(HttpServletRequest request) {
        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);
        if (matchedRoute != null && matchedRoute.getRoute().getProtocolType() != null) {
            return matchedRoute.getRoute().getProtocolType();
        }
        return "REST";
    }

    /**
     * Returns true for protocols where caching should be skipped entirely.
     */
    private boolean isNonCacheableProtocol(String protocolType) {
        if (protocolType == null) {
            return false;
        }
        String upper = protocolType.toUpperCase();
        return "GRPC".equals(upper) || "WEBSOCKET".equals(upper) || "SSE".equals(upper);
    }

    /**
     * Build a cache key from: method + path + sorted query params.
     * Adjusts key based on protocol type:
     * - GRAPHQL: includes the operationName from the request body JSON
     * - SOAP: includes the SOAPAction header
     * - REST (default): method + URI + query params + Accept header
     */
    private String buildCacheKey(HttpServletRequest request, String protocolType) {
        StringBuilder key = new StringBuilder();
        key.append(request.getMethod()).append(":");
        key.append(request.getRequestURI());

        // Sorted query parameters
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            String[] params = queryString.split("&");
            Arrays.sort(params);
            key.append("?").append(String.join("&", params));
        }

        // Protocol-specific cache key parts
        if ("GRAPHQL".equalsIgnoreCase(protocolType)) {
            String operationName = extractGraphqlOperationName(request);
            if (operationName != null && !operationName.isEmpty()) {
                key.append("|op=").append(operationName);
            }
        } else if ("SOAP".equalsIgnoreCase(protocolType)) {
            String soapAction = request.getHeader("SOAPAction");
            if (soapAction != null && !soapAction.isEmpty()) {
                key.append("|soapAction=").append(soapAction);
            }
        }

        // Include configurable headers (e.g., Accept)
        String accept = request.getHeader("Accept");
        if (accept != null) {
            key.append("|accept=").append(accept);
        }

        // Include API ID from matched route if available
        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);
        if (matchedRoute != null && matchedRoute.getRoute().getApiId() != null) {
            key.append("|api=").append(matchedRoute.getRoute().getApiId());
        }

        return key.toString();
    }

    /**
     * Extract the GraphQL operationName from the request body JSON.
     */
    private String extractGraphqlOperationName(HttpServletRequest request) {
        try {
            byte[] body = null;
            if (request instanceof ContentCachingRequestWrapper wrapper) {
                // Force reading the body so it's cached
                wrapper.getInputStream().readAllBytes();
                body = wrapper.getContentAsByteArray();
            }
            if (body != null && body.length > 0) {
                JsonNode json = objectMapper.readTree(body);
                JsonNode opNode = json.get("operationName");
                if (opNode != null && !opNode.isNull()) {
                    return opNode.asText();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract GraphQL operationName from request body: {}", e.getMessage());
        }
        return null;
    }

    private void writeCachedResponse(HttpServletResponse response, CachedResponse cached) throws IOException {
        response.setStatus(cached.getStatusCode());
        if (cached.getHeaders() != null) {
            cached.getHeaders().forEach(response::setHeader);
        }
        response.setHeader("X-Cache", "HIT");
        response.setHeader("X-Cache-Time", cached.getCachedAt().toString());

        // Set ETag header
        if (cached.getEtag() != null) {
            response.setHeader("ETag", cached.getEtag());
        }

        // Set Last-Modified header from cached timestamp
        if (cached.getCachedAt() != null) {
            response.setHeader("Last-Modified", formatHttpDate(cached.getCachedAt()));
        }

        if (cached.getBody() != null) {
            response.getOutputStream().write(cached.getBody());
            response.getOutputStream().flush();
        }
    }

    /**
     * Compute an ETag value as the MD5 hex digest of the response body,
     * wrapped in double-quotes per HTTP spec (e.g. "a1b2c3...").
     */
    private String computeEtag(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(body);
            StringBuilder hex = new StringBuilder("\"");
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            hex.append("\"");
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 algorithm not available for ETag computation", e);
            return null;
        }
    }

    /**
     * Format an Instant as an HTTP-date string (RFC 1123).
     */
    private String formatHttpDate(Instant instant) {
        return HTTP_DATE_FORMATTER.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private Map<String, String> extractResponseHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : response.getHeaderNames()) {
            String value = response.getHeader(headerName);
            if (value != null) {
                headers.put(headerName, value);
            }
        }
        return headers;
    }
}
