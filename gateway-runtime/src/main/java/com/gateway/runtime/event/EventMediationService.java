package com.gateway.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mediation service that bridges RabbitMQ queues to WebSocket, SSE, and
 * REST long-poll consumers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventMediationService {

    private final EventGatewayService eventGatewayService;

    /** topic → list of active SSE emitters */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> sseEmitters =
            new ConcurrentHashMap<>();

    /** topic → buffered messages for REST polling */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> pollBuffers =
            new ConcurrentHashMap<>();

    /** topic → subscription ID from EventGatewayService (to avoid duplicate subscriptions) */
    private final ConcurrentHashMap<String, String> topicSubscriptions = new ConcurrentHashMap<>();

    // ── WebSocket Bridge ─────────────────────────────────────────────────

    /**
     * Subscribe to a RabbitMQ queue and relay messages to a WebSocket session.
     * Returns the subscription ID.
     */
    public String bridgeToWebSocket(String queue, WebSocketSession session) {
        return eventGatewayService.subscribeToQueue(queue, message -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.error("Failed to send message to WebSocket session {}: {}",
                        session.getId(), e.getMessage());
            }
        });
    }

    // ── SSE Bridge ───────────────────────────────────────────────────────

    /**
     * Register an SSE emitter for a topic. Ensures the topic has an active
     * broker subscription that fans out to all registered emitters.
     */
    public SseEmitter bridgeToSse(String topic, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        sseEmitters.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeSseEmitter(topic, emitter));
        emitter.onTimeout(() -> removeSseEmitter(topic, emitter));
        emitter.onError(e -> removeSseEmitter(topic, emitter));

        ensureTopicSubscription(topic);

        log.debug("SSE emitter registered for topic={}", topic);
        return emitter;
    }

    private void removeSseEmitter(String topic, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = sseEmitters.get(topic);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    // ── REST Long-Poll ───────────────────────────────────────────────────

    /**
     * Return buffered messages for a topic (REST poll).  Drains the buffer.
     */
    public List<String> pollMessages(String topic) {
        ensureTopicSubscription(topic);
        CopyOnWriteArrayList<String> buffer = pollBuffers.get(topic);
        if (buffer == null || buffer.isEmpty()) {
            return List.of();
        }
        List<String> messages = List.copyOf(buffer);
        buffer.clear();
        return messages;
    }

    // ── Shared Topic Subscription ────────────────────────────────────────

    /**
     * Ensure there is exactly one broker subscription per topic that fans
     * out to both SSE emitters and the poll buffer.
     */
    private void ensureTopicSubscription(String topic) {
        topicSubscriptions.computeIfAbsent(topic, t -> {
            pollBuffers.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>());

            return eventGatewayService.subscribeToQueue(t, message -> {
                // Fan out to SSE emitters
                CopyOnWriteArrayList<SseEmitter> emitters = sseEmitters.get(t);
                if (emitters != null) {
                    for (SseEmitter emitter : emitters) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("event")
                                    .data(message));
                        } catch (Exception e) {
                            emitters.remove(emitter);
                        }
                    }
                }

                // Buffer for REST polling
                CopyOnWriteArrayList<String> buffer = pollBuffers.get(t);
                if (buffer != null) {
                    buffer.add(message);
                    // Cap buffer at 10 000 messages
                    while (buffer.size() > 10_000) {
                        buffer.remove(0);
                    }
                }
            });
        });
    }
}
