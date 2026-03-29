package com.gateway.runtime.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for the event gateway.
 * Registers the {@link EventWebSocketHandler} at {@code /v1/events/ws}.
 */
@Configuration
@RequiredArgsConstructor
public class EventWebSocketConfig implements WebSocketConfigurer {

    private final EventWebSocketHandler eventWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventWebSocketHandler, "/v1/events/ws")
                .setAllowedOrigins("*");
    }
}
