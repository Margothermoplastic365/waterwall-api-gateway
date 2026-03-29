package com.gateway.runtime.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GraphQL protocol proxy handler. Forwards GraphQL queries and mutations to
 * the upstream GraphQL endpoint. Subscription requests over HTTP are rejected
 * with a 400 status (subscriptions require WebSocket).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphQLProxyHandler implements ProtocolProxyHandler {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"
    );

    /**
     * Pattern to detect the operation type from a GraphQL query string.
     * Matches "query", "mutation", or "subscription" at the start (with optional whitespace).
     */
    private static final Pattern OPERATION_TYPE_PATTERN = Pattern.compile(
            "^\\s*(query|mutation|subscription)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract the operation name from a GraphQL query string.
     * Matches: query OperationName or mutation OperationName, etc.
     */
    private static final Pattern OPERATION_NAME_PATTERN = Pattern.compile(
            "^\\s*(?:query|mutation|subscription)\\s+(\\w+)", Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract the "query" field from the JSON body (simple extraction).
     */
    private static final Pattern QUERY_FIELD_PATTERN = Pattern.compile(
            "\"query\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL
    );

    private final RestClient restClient;

    @Override
    public String getProtocolType() {
        return "GRAPHQL";
    }

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute) {
        long startTime = System.currentTimeMillis();
        GatewayRoute route = matchedRoute.getRoute();
        String upstreamUrl = route.getUpstreamUrl();

        // GraphQL requires POST
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return ResponseEntity.status(405)
                    .body("{\"error\":\"method_not_allowed\",\"message\":\"GraphQL endpoint requires POST\"}".getBytes());
        }

        try {
            byte[] body = request.getInputStream().readAllBytes();
            String bodyStr = new String(body);

            // Extract and analyze the query
            String queryStr = extractQueryField(bodyStr);
            String operationType = detectOperationType(queryStr);
            String operationName = extractOperationName(queryStr);

            log.info("GraphQL proxy {} -> {} operation={} type={} name={}",
                    request.getRequestURI(), upstreamUrl,
                    operationType, operationType, operationName);

            request.setAttribute("gateway.graphql.operationType", operationType);
            request.setAttribute("gateway.graphql.operationName", operationName);

            // Reject subscriptions over HTTP — they require WebSocket
            if ("subscription".equalsIgnoreCase(operationType)) {
                long latency = System.currentTimeMillis() - startTime;
                request.setAttribute("gateway.proxyLatencyMs", latency);
                return ResponseEntity.status(400)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(("{\"errors\":[{\"message\":\"Subscriptions require WebSocket transport. "
                                + "Connect via ws:// or wss:// instead.\"}]}").getBytes());
            }

            // Forward to upstream GraphQL service
            RestClient.RequestBodySpec requestSpec = restClient
                    .method(HttpMethod.POST)
                    .uri(URI.create(upstreamUrl))
                    .headers(headers -> {
                        copyRequestHeaders(request, headers, upstreamUrl);
                        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    });

            requestSpec.body(body);

            ResponseEntity<byte[]> upstreamResponse = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Do not throw — forward upstream error as-is
                    })
                    .toEntity(byte[].class);

            HttpHeaders responseHeaders = new HttpHeaders();
            upstreamResponse.getHeaders().forEach((name, values) -> {
                if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    responseHeaders.put(name, values);
                }
            });

            long latency = System.currentTimeMillis() - startTime;
            request.setAttribute("gateway.proxyLatencyMs", latency);

            return ResponseEntity
                    .status(upstreamResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(upstreamResponse.getBody());

        } catch (ResourceAccessException ex) {
            long latency = System.currentTimeMillis() - startTime;
            request.setAttribute("gateway.proxyLatencyMs", latency);

            Throwable cause = ex.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                log.error("GraphQL upstream timeout for {}: {}", upstreamUrl, cause.getMessage());
                request.setAttribute("gateway.errorCode", "UPSTREAM_TIMEOUT");
                return ResponseEntity.status(504)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"errors\":[{\"message\":\"Upstream GraphQL service timed out\"}]}".getBytes());
            }
            if (cause instanceof java.net.ConnectException) {
                log.error("GraphQL upstream connection refused for {}: {}", upstreamUrl, cause.getMessage());
                request.setAttribute("gateway.errorCode", "UPSTREAM_CONNECT_REFUSED");
                return ResponseEntity.status(502)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"errors\":[{\"message\":\"Upstream GraphQL service unavailable\"}]}".getBytes());
            }

            log.error("GraphQL upstream error for {}: {}", upstreamUrl, ex.getMessage());
            request.setAttribute("gateway.errorCode", "UPSTREAM_ERROR");
            return ResponseEntity.status(502)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"errors\":[{\"message\":\"Error communicating with upstream GraphQL service\"}]}".getBytes());

        } catch (IOException ex) {
            long latency = System.currentTimeMillis() - startTime;
            request.setAttribute("gateway.proxyLatencyMs", latency);

            log.error("Failed to read GraphQL request body: {}", ex.getMessage());
            request.setAttribute("gateway.errorCode", "REQUEST_READ_ERROR");
            return ResponseEntity.status(502)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"errors\":[{\"message\":\"Failed to read request body\"}]}".getBytes());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Extract the "query" field value from a JSON body string using simple regex.
     */
    private String extractQueryField(String body) {
        Matcher m = QUERY_FIELD_PATTERN.matcher(body);
        if (m.find()) {
            return m.group(1).replace("\\n", "\n").replace("\\\"", "\"");
        }
        return body; // fallback: treat entire body as query
    }

    /**
     * Detect operation type: query, mutation, or subscription.
     * Defaults to "query" if not explicitly stated (per GraphQL spec).
     */
    private String detectOperationType(String query) {
        if (query == null || query.isBlank()) {
            return "query";
        }
        Matcher m = OPERATION_TYPE_PATTERN.matcher(query);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        // If query starts with { it's a shorthand query
        return "query";
    }

    /**
     * Extract the operation name from the query string, if present.
     */
    private String extractOperationName(String query) {
        if (query == null || query.isBlank()) {
            return "anonymous";
        }
        Matcher m = OPERATION_NAME_PATTERN.matcher(query);
        if (m.find()) {
            return m.group(1);
        }
        return "anonymous";
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders targetHeaders, String upstreamUrl) {
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

        try {
            URI uri = new URI(upstreamUrl);
            String host = uri.getHost();
            if (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443) {
                host = host + ":" + uri.getPort();
            }
            targetHeaders.set(HttpHeaders.HOST, host);
        } catch (URISyntaxException e) {
            log.warn("Could not parse upstream URL for Host header: {}", upstreamUrl);
        }
    }
}
