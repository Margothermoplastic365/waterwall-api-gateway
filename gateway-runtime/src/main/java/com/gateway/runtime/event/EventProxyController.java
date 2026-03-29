package com.gateway.runtime.event;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * REST proxy controller for the event gateway.
 * Provides HTTP endpoints for publishing, subscribing, and managing queues
 * on the customer's RabbitMQ broker.
 */
@Slf4j
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventProxyController {

    private final EventGatewayService eventGatewayService;
    private final EventAccessControl eventAccessControl;
    private final EventSchemaValidator eventSchemaValidator;

    /** In-memory registry of active subscriptions with buffered messages for polling. */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> subscriptionBuffers =
            new ConcurrentHashMap<>();

    // ── Publish ──────────────────────────────────────────────────────────

    @PostMapping("/publish")
    public ResponseEntity<?> publishMessage(@RequestBody PublishRequest request) {
        // Access control
        if (!eventAccessControl.canPublish(request.getExchange(), request.getRoutingKey())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorised to publish to this exchange/routing key"));
        }

        // Schema validation
        EventSchemaValidator.ValidationResult validation =
                eventSchemaValidator.validate(request.getExchange(), request.getMessage());
        if (!validation.isValid()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Schema validation failed", "details", validation.getErrors()));
        }

        try {
            eventGatewayService.publishToExchange(
                    request.getExchange(), request.getRoutingKey(), request.getMessage());
            return ResponseEntity.ok(Map.of("status", "published"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to publish message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to publish message"));
        }
    }

    // ── Subscribe ────────────────────────────────────────────────────────

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscribeRequest request) {
        if (!eventAccessControl.canSubscribe(request.getQueue())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorised to subscribe to this queue"));
        }

        try {
            CopyOnWriteArrayList<String> buffer = new CopyOnWriteArrayList<>();
            String subscriptionId = eventGatewayService.subscribeToQueue(
                    request.getQueue(), buffer::add);
            subscriptionBuffers.put(subscriptionId, buffer);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("subscriptionId", subscriptionId, "queue", request.getQueue()));
        } catch (Exception e) {
            log.error("Failed to subscribe to queue {}: {}", request.getQueue(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create subscription"));
        }
    }

    @DeleteMapping("/subscribe/{id}")
    public ResponseEntity<?> unsubscribe(@PathVariable("id") String subscriptionId) {
        boolean removed = eventGatewayService.unsubscribe(subscriptionId);
        subscriptionBuffers.remove(subscriptionId);
        if (removed) {
            return ResponseEntity.ok(Map.of("status", "unsubscribed", "subscriptionId", subscriptionId));
        }
        return ResponseEntity.notFound().build();
    }

    // ── Queue Management ─────────────────────────────────────────────────

    @GetMapping("/queues")
    public ResponseEntity<?> listQueues() {
        // Return list of queues known from active subscriptions
        List<String> knownQueues = subscriptionBuffers.keySet().stream()
                .map(id -> id.substring(0, id.lastIndexOf('-')))
                .distinct()
                .toList();
        return ResponseEntity.ok(Map.of("queues", knownQueues));
    }

    @PostMapping("/queues")
    public ResponseEntity<?> createQueue(@RequestBody CreateQueueRequest request) {
        try {
            String queueName = eventGatewayService.createQueue(request.getName(), request.getArgs());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("queue", queueName, "status", "created"));
        } catch (Exception e) {
            log.error("Failed to create queue {}: {}", request.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create queue"));
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────

    @Data
    public static class PublishRequest {
        private String exchange;
        private String routingKey;
        private String message;
    }

    @Data
    public static class SubscribeRequest {
        private String queue;
    }

    @Data
    public static class CreateQueueRequest {
        private String name;
        private Map<String, Object> args;
    }
}
