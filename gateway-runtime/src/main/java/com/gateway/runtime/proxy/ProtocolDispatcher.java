package com.gateway.runtime.proxy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.gateway.runtime.model.MatchedRoute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

/**
 * Registry of protocol proxy handlers. On construction it auto-discovers all
 * {@link ProtocolProxyHandler} beans and indexes them by protocol type.
 * The {@link com.gateway.runtime.controller.ProxyController} delegates here.
 */
@Slf4j
@Component
public class ProtocolDispatcher {

    private final Map<String, ProtocolProxyHandler> handlers;

    public ProtocolDispatcher(List<ProtocolProxyHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(
                        h -> h.getProtocolType().toUpperCase(),
                        Function.identity()
                ));
        log.info("Registered protocol proxy handlers: {}", handlers.keySet());
    }

    /**
     * Dispatch a request to the appropriate protocol handler.
     *
     * @param protocolType the protocol type from the matched route (e.g. "REST", "SOAP")
     * @param request      the incoming HTTP request
     * @param matchedRoute the matched route
     * @return the upstream response, or 501 if the protocol is not supported
     */
    public ResponseEntity<byte[]> dispatch(String protocolType, HttpServletRequest request, MatchedRoute matchedRoute) {
        String key = (protocolType == null) ? "REST" : protocolType.toUpperCase();

        ProtocolProxyHandler handler = handlers.get(key);
        if (handler == null) {
            log.warn("No handler registered for protocol type: {}", key);
            return ResponseEntity.status(501)
                    .body(("{\"error\":\"not_implemented\",\"message\":\"Protocol '" + key + "' is not supported\"}")
                            .getBytes());
        }

        return handler.proxy(request, matchedRoute);
    }
}
