package com.gateway.runtime.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.proxy.WebSocketProxyHandler;
import com.gateway.runtime.service.RouteMatcherService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the WebSocket proxy handler for all paths.
 * The handshake interceptor matches the incoming request against gateway routes
 * and only allows the upgrade if a WEBSOCKET route matches.
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketProxyHandler webSocketProxyHandler;
    private final RouteMatcherService routeMatcherService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketProxyHandler, "/**")
                .addInterceptors(new GatewayWebSocketInterceptor())
                .setAllowedOrigins("*");
    }

    /**
     * Handshake interceptor that checks if the request matches a WEBSOCKET route.
     * If so, it stores the upstream URL in session attributes for the handler to use.
     */
    private class GatewayWebSocketInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                        WebSocketHandler wsHandler, Map<String, Object> attributes) {

            if (request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest httpRequest = servletRequest.getServletRequest();
                String requestPath = httpRequest.getRequestURI();
                String method = httpRequest.getMethod();

                MatchedRoute matchedRoute = routeMatcherService.match(requestPath, method);
                if (matchedRoute == null) {
                    log.debug("WebSocket handshake rejected — no route matched for {}", requestPath);
                    return false;
                }

                GatewayRoute route = matchedRoute.getRoute();
                String protocolType = route.getProtocolType();

                if (!"WEBSOCKET".equalsIgnoreCase(protocolType)) {
                    log.debug("WebSocket handshake rejected — route {} is not WEBSOCKET type", requestPath);
                    return false;
                }

                // Convert HTTP upstream URL to WebSocket URL
                String upstreamUrl = route.getUpstreamUrl();
                if (upstreamUrl.startsWith("http://")) {
                    upstreamUrl = "ws://" + upstreamUrl.substring(7);
                } else if (upstreamUrl.startsWith("https://")) {
                    upstreamUrl = "wss://" + upstreamUrl.substring(8);
                }

                // Append remaining path if strip prefix
                String remainingPath = requestPath;
                if (route.isStripPrefix() && route.getPath() != null) {
                    String staticPrefix = route.getPath().replaceAll("/\\*\\*$", "")
                            .replaceAll("/\\{[^}]+}.*", "");
                    if (!staticPrefix.isEmpty() && requestPath.startsWith(staticPrefix)) {
                        remainingPath = requestPath.substring(staticPrefix.length());
                    }
                }

                if (!remainingPath.isEmpty() && !remainingPath.equals("/")) {
                    if (upstreamUrl.endsWith("/") && remainingPath.startsWith("/")) {
                        upstreamUrl = upstreamUrl + remainingPath.substring(1);
                    } else if (!upstreamUrl.endsWith("/") && !remainingPath.startsWith("/")) {
                        upstreamUrl = upstreamUrl + "/" + remainingPath;
                    } else {
                        upstreamUrl = upstreamUrl + remainingPath;
                    }
                }

                attributes.put("gateway.ws.upstreamUrl", upstreamUrl);
                log.info("WebSocket handshake accepted for {} -> {}", requestPath, upstreamUrl);
                return true;
            }

            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Exception exception) {
            // No-op
        }
    }
}
