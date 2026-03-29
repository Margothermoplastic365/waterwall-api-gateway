package com.gateway.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Servlet filter that populates SLF4J MDC with distributed trace context for
 * every request.
 *
 * <ul>
 *   <li>Reads or generates a trace ID (X-Trace-Id header)</li>
 *   <li>Generates a new span ID per service hop (X-Span-Id header)</li>
 *   <li>Extracts userId and orgId from JWT claims when an Authorization Bearer token is present</li>
 *   <li>Stores traceId, spanId, parentSpanId, service, userId, and orgId in MDC so they appear in every log line</li>
 *   <li>Propagates X-Trace-Id, X-Span-Id, and X-Parent-Span-Id on the response for downstream consumption</li>
 *   <li>Adds X-Request-Duration header with the total request processing time in milliseconds</li>
 *   <li>Clears MDC after the request completes</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    private static final String REQUEST_DURATION_HEADER = "X-Request-Duration";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";
    private static final String MDC_PARENT_SPAN_ID = "parentSpanId";
    private static final String MDC_SERVICE = "service";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_ORG_ID = "orgId";

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        long startNanos = System.nanoTime();

        try {
            // --- Trace ID: reuse from upstream or generate a new root ---
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_TRACE_ID, traceId);

            // --- Span ID: always generate a new one for this hop ---
            String incomingSpanId = request.getHeader(SPAN_ID_HEADER);
            String spanId = UUID.randomUUID().toString().substring(0, 16);
            MDC.put(MDC_SPAN_ID, spanId);

            // If caller sent a span ID, treat it as our parent span
            if (incomingSpanId != null && !incomingSpanId.isBlank()) {
                MDC.put(MDC_PARENT_SPAN_ID, incomingSpanId);
                response.setHeader(PARENT_SPAN_ID_HEADER, incomingSpanId);
            }

            // --- Service name ---
            MDC.put(MDC_SERVICE, serviceName);

            // --- Propagate trace headers on response ---
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(SPAN_ID_HEADER, spanId);

            // --- JWT claim extraction (userId, orgId) ---
            extractJwtClaims(request);

            chain.doFilter(request, response);

        } finally {
            // --- Request duration header ---
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            response.setHeader(REQUEST_DURATION_HEADER, String.valueOf(durationMs));

            MDC.clear();
        }
    }

    /**
     * Decodes the JWT payload (without signature verification) to extract userId
     * and orgId claims for logging purposes only.
     */
    private void extractJwtClaims(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            // JWT structure: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return;
            }

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            // Lightweight extraction without a JSON library dependency.
            // Looks for "sub", "userId", and "orgId" claims.
            String userId = extractJsonStringValue(payloadJson, "sub");
            if (userId == null) {
                userId = extractJsonStringValue(payloadJson, "userId");
            }
            String orgId = extractJsonStringValue(payloadJson, "orgId");

            if (userId != null) {
                MDC.put(MDC_USER_ID, userId);
            }
            if (orgId != null) {
                MDC.put(MDC_ORG_ID, orgId);
            }

        } catch (IllegalArgumentException e) {
            // Malformed Base64 — not a valid JWT; silently skip.
            log.trace("Could not decode JWT payload for MDC extraction", e);
        }
    }

    /**
     * Extracts a simple string value for the given key from a flat JSON object.
     * This intentionally avoids pulling in a JSON parser dependency just for logging.
     * It handles the common case: {@code "key":"value"} (with optional spaces).
     *
     * @return the value, or {@code null} if not found
     */
    static String extractJsonStringValue(String json, String key) {
        // Pattern: "key" : "value"  (with optional whitespace)
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) {
            return null;
        }

        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) {
            return null;
        }

        // Find the opening quote of the value
        int openQuote = json.indexOf('"', colonIdx + 1);
        if (openQuote < 0) {
            return null;
        }

        // Find the closing quote (handle escaped quotes)
        int closeQuote = openQuote + 1;
        while (closeQuote < json.length()) {
            if (json.charAt(closeQuote) == '"' && json.charAt(closeQuote - 1) != '\\') {
                break;
            }
            closeQuote++;
        }
        if (closeQuote >= json.length()) {
            return null;
        }

        return json.substring(openQuote + 1, closeQuote);
    }
}
