package com.gateway.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HTTP-to-Event bridge — receives HTTP POST payloads and publishes them
 * as events to the broker.  Supports JSON and plain-text payloads.
 */
@Slf4j
@RestController
@RequestMapping("/v1/events/bridge")
@RequiredArgsConstructor
public class HttpToEventBridge {

    private final EventGatewayService eventGatewayService;
    private final EventAccessControl eventAccessControl;
    private final EventSchemaValidator eventSchemaValidator;

    /**
     * POST /v1/events/bridge/{topic} — publish the request body as an event.
     *
     * @param topic       used both as the exchange name and the routing key
     * @param routingKey  optional explicit routing key (defaults to topic)
     * @param body        the message payload (JSON or plain text)
     */
    @PostMapping(value = "/{topic}", consumes = {"application/json", "text/plain"})
    public ResponseEntity<?> bridgeHttpToEvent(
            @PathVariable("topic") String topic,
            @RequestParam(value = "routingKey", required = false) String routingKey,
            @RequestBody String body) {

        if (!eventAccessControl.canPublish(topic, routingKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorised to publish to topic: " + topic));
        }

        // Schema validation
        EventSchemaValidator.ValidationResult validation = eventSchemaValidator.validate(topic, body);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Schema validation failed", "details", validation.getErrors()));
        }

        String rk = (routingKey != null && !routingKey.isBlank()) ? routingKey : topic;

        try {
            eventGatewayService.publishToExchange(topic, rk, body);
            return ResponseEntity.ok(Map.of(
                    "status", "published",
                    "exchange", topic,
                    "routingKey", rk
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to bridge HTTP to event for topic={}: {}", topic, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to publish event"));
        }
    }
}
