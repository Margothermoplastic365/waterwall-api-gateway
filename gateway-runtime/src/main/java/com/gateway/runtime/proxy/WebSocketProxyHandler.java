package com.gateway.runtime.proxy;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket proxy handler that relays frames bidirectionally between the
 * client and an upstream WebSocket service using virtual threads.
 *
 * <p>This is NOT a {@link ProtocolProxyHandler} since WebSocket connections
 * bypass the normal HTTP request/response cycle. Instead, it acts as a
 * Spring {@link WebSocketHandler} registered via {@link com.gateway.runtime.config.WebSocketConfig}.</p>
 */
@Slf4j
@Component
public class WebSocketProxyHandler implements WebSocketHandler {

    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        // The upstream URL is set as a URI attribute by WebSocketConfig's interceptor
        URI upstreamUri = clientSession.getUri();
        String upstreamUrl = (String) clientSession.getAttributes().get("gateway.ws.upstreamUrl");

        if (upstreamUrl == null) {
            log.error("No upstream URL found for WebSocket session {}", clientSession.getId());
            clientSession.close(CloseStatus.SERVER_ERROR);
            return;
        }

        log.info("WebSocket connection established, client={} upstream={}", clientSession.getId(), upstreamUrl);

        long startTime = System.currentTimeMillis();
        AtomicLong clientToUpstreamCount = new AtomicLong(0);
        AtomicLong upstreamToClientCount = new AtomicLong(0);
        AtomicBoolean closed = new AtomicBoolean(false);

        // Connect to upstream WebSocket
        WebSocketSession upstreamSession;
        try {
            upstreamSession = webSocketClient
                    .execute(new WebSocketHandler() {
                        @Override
                        public void afterConnectionEstablished(WebSocketSession session) {
                            log.debug("Upstream WebSocket connected: {}", session.getId());
                        }

                        @Override
                        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                            // VT-2: relay upstream -> client
                            if (clientSession.isOpen()) {
                                clientSession.sendMessage(message);
                                upstreamToClientCount.incrementAndGet();
                            }
                        }

                        @Override
                        public void handleTransportError(WebSocketSession session, Throwable exception) {
                            log.warn("Upstream WebSocket transport error: {}", exception.getMessage());
                            closeQuietly(clientSession, closed);
                        }

                        @Override
                        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                            log.debug("Upstream WebSocket closed: {}", status);
                            closeQuietly(clientSession, closed);
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("WebSocket session ended — duration={}ms client->upstream={} upstream->client={}",
                                    duration, clientToUpstreamCount.get(), upstreamToClientCount.get());
                        }

                        @Override
                        public boolean supportsPartialMessages() {
                            return false;
                        }
                    }, upstreamUrl)
                    .get();
        } catch (Exception ex) {
            log.error("Failed to connect to upstream WebSocket {}: {}", upstreamUrl, ex.getMessage());
            clientSession.close(CloseStatus.SERVER_ERROR);
            return;
        }

        // Store upstream session and counters as attributes for handleMessage
        clientSession.getAttributes().put("gateway.ws.upstreamSession", upstreamSession);
        clientSession.getAttributes().put("gateway.ws.clientToUpstreamCount", clientToUpstreamCount);
        clientSession.getAttributes().put("gateway.ws.closed", closed);
        clientSession.getAttributes().put("gateway.ws.startTime", startTime);
        clientSession.getAttributes().put("gateway.ws.upstreamToClientCount", upstreamToClientCount);
    }

    @Override
    public void handleMessage(WebSocketSession clientSession, WebSocketMessage<?> message) throws Exception {
        // VT-1: relay client -> upstream
        WebSocketSession upstreamSession = (WebSocketSession) clientSession.getAttributes().get("gateway.ws.upstreamSession");
        AtomicLong clientToUpstreamCount = (AtomicLong) clientSession.getAttributes().get("gateway.ws.clientToUpstreamCount");

        if (upstreamSession != null && upstreamSession.isOpen()) {
            upstreamSession.sendMessage(message);
            if (clientToUpstreamCount != null) {
                clientToUpstreamCount.incrementAndGet();
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) throws Exception {
        log.warn("Client WebSocket transport error for {}: {}", clientSession.getId(), exception.getMessage());
        WebSocketSession upstreamSession = (WebSocketSession) clientSession.getAttributes().get("gateway.ws.upstreamSession");
        AtomicBoolean closed = (AtomicBoolean) clientSession.getAttributes().get("gateway.ws.closed");
        if (upstreamSession != null) {
            closeQuietly(upstreamSession, closed);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) throws Exception {
        log.debug("Client WebSocket closed: {} status={}", clientSession.getId(), status);
        WebSocketSession upstreamSession = (WebSocketSession) clientSession.getAttributes().get("gateway.ws.upstreamSession");
        AtomicBoolean closed = (AtomicBoolean) clientSession.getAttributes().get("gateway.ws.closed");
        if (upstreamSession != null) {
            closeQuietly(upstreamSession, closed);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void closeQuietly(WebSocketSession session, AtomicBoolean closed) {
        if (closed != null && !closed.compareAndSet(false, true)) {
            return; // Already closing
        }
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException e) {
            log.debug("Error closing WebSocket session {}: {}", session.getId(), e.getMessage());
        }
    }
}
