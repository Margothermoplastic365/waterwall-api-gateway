package com.gateway.runtime.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.proxy.ProtocolDispatcher;
import com.gateway.runtime.proxy.SseProxyHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Catch-all proxy controller that forwards every request that has survived the
 * filter pipeline to the matched upstream service. Delegates to protocol-specific
 * handlers via {@link ProtocolDispatcher}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProtocolDispatcher protocolDispatcher;

    @RequestMapping("/**")
    public Object proxy(HttpServletRequest request, HttpServletResponse response) {

        // ── 1. Retrieve matched route from filter pipeline ───────────────
        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute("gateway.matchedRoute");
        if (matchedRoute == null) {
            log.warn("No matched route found for {} {}", request.getMethod(), request.getRequestURI());
            return ResponseEntity.status(404)
                    .body("{\"error\":\"no_route\",\"message\":\"No route matched\"}".getBytes());
        }

        GatewayRoute route = matchedRoute.getRoute();

        // ── 2. Determine protocol type ───────────────────────────────────
        String protocolType = route.getProtocolType();
        if (protocolType == null || protocolType.isBlank()) {
            protocolType = "REST"; // default
        }

        // WebSocket is handled by WebSocketConfig — should not reach here
        if ("WEBSOCKET".equalsIgnoreCase(protocolType)) {
            log.warn("WebSocket request reached ProxyController — should be handled by WebSocketConfig");
            return ResponseEntity.status(426)
                    .body("{\"error\":\"upgrade_required\",\"message\":\"WebSocket upgrade required\"}".getBytes());
        }

        log.debug("Dispatching {} {} via {} protocol", request.getMethod(), request.getRequestURI(), protocolType);

        // ── 3. Delegate to protocol handler ──────────────────────────────
        ResponseEntity<byte[]> result = protocolDispatcher.dispatch(protocolType, request, matchedRoute);

        // SSE handler returns null and sets an SseEmitter as attribute
        if (result == null) {
            SseEmitter emitter = (SseEmitter) request.getAttribute(SseProxyHandler.SSE_EMITTER_ATTR);
            if (emitter != null) {
                return emitter;
            }
            // Fallback if no emitter
            return ResponseEntity.status(500)
                    .body("{\"error\":\"internal_error\",\"message\":\"Protocol handler returned no response\"}".getBytes());
        }

        return result;
    }
}
