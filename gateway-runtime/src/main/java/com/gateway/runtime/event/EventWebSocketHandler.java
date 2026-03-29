package com.gateway.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for the event gateway.
 *
 * <p>On connect the client specifies a queue (via query parameter {@code queue})
 * and an optional exchange ({@code exchange}).  The handler subscribes to the
 * queue and relays broker messages to the client.  Messages sent from the
 * client are published to the exchange (bidirectional bridge).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventWebSocketHandler extends TextWebSocketHandler {

    private final EventGatewayService eventGatewayService;
    private final EventMediationService eventMediationService;

    /** session id → subscription id */
    private final ConcurrentHashMap<String, String> sessionSubscriptions = new ConcurrentHashMap<>();
    /** session id → exchange name for publishing */
    private final ConcurrentHashMap<String, String> sessionExchanges = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String queue = extractParam(session, "queue");
        String exchange = extractParam(session, "exchange");

        if (queue == null || queue.isBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("Missing 'queue' query parameter"));
            return;
        }

        String subscriptionId = eventMediationService.bridgeToWebSocket(queue, session);
        sessionSubscriptions.put(session.getId(), subscriptionId);

        if (exchange != null && !exchange.isBlank()) {
            sessionExchanges.put(session.getId(), exchange);
        }

        log.info("Event WebSocket connected: sessionId={} queue={} exchange={}",
                session.getId(), queue, exchange);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String exchange = sessionExchanges.get(session.getId());
        if (exchange != null) {
            // Publish client message to the exchange (bidirectional)
            String routingKey = extractParam(session, "routingKey");
            if (routingKey == null) routingKey = "";
            eventGatewayService.publishToExchange(exchange, routingKey, message.getPayload());
            log.debug("Client message published to exchange={} via session={}", exchange, session.getId());
        } else {
            log.debug("Ignoring client message — no exchange configured for session={}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String subscriptionId = sessionSubscriptions.remove(session.getId());
        sessionExchanges.remove(session.getId());

        if (subscriptionId != null) {
            eventGatewayService.unsubscribe(subscriptionId);
        }

        log.info("Event WebSocket disconnected: sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error sessionId={}: {}", session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String extractParam(WebSocketSession session, String paramName) {
        Map<String, String> params = parseQueryString(session.getUri() != null ? session.getUri().getQuery() : null);
        return params.get(paramName);
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}
