package com.gateway.runtime.proxy;

import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * Strategy interface for protocol-specific proxy handlers.
 * Each implementation registers itself for a specific protocol type
 * (REST, SOAP, GRAPHQL, SSE, etc.).
 */
public interface ProtocolProxyHandler {

    /**
     * The protocol type this handler supports (e.g. "REST", "SOAP", "GRAPHQL", "SSE").
     */
    String getProtocolType();

    /**
     * Proxy the incoming request to the upstream service.
     *
     * @param request      the incoming HTTP request
     * @param matchedRoute the matched route containing upstream URL and config
     * @return the upstream response forwarded to the client
     */
    ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute);
}
