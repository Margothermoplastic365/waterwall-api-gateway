package com.gateway.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * SSE and long-poll endpoints for consuming events from the broker.
 */
@Slf4j
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventSseController {

    private static final long DEFAULT_SSE_TIMEOUT_MS = 300_000; // 5 minutes

    private final EventMediationService eventMediationService;
    private final EventAccessControl eventAccessControl;

    /**
     * Stream events from a topic as Server-Sent Events.
     */
    @GetMapping(value = "/stream/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable("topic") String topic,
                                   @RequestParam(value = "timeout", required = false) Long timeoutMs) {
        if (!eventAccessControl.canAccessTopic(topic)) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new SecurityException("Not authorised to access topic: " + topic));
            return emitter;
        }

        long timeout = timeoutMs != null ? timeoutMs : DEFAULT_SSE_TIMEOUT_MS;
        log.info("SSE stream requested for topic={} timeout={}ms", topic, timeout);
        return eventMediationService.bridgeToSse(topic, timeout);
    }

    /**
     * Long-poll endpoint — returns buffered messages for the topic.
     * The client can specify a timeout (not used server-side for now;
     * the client simply retries).
     */
    @GetMapping("/poll/{topic}")
    public ResponseEntity<?> pollEvents(@PathVariable("topic") String topic,
                                         @RequestParam(value = "timeout", defaultValue = "30") int timeoutSeconds) {
        if (!eventAccessControl.canAccessTopic(topic)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Not authorised to access topic: " + topic));
        }

        List<String> messages = eventMediationService.pollMessages(topic);
        return ResponseEntity.ok(Map.of("topic", topic, "messages", messages, "count", messages.size()));
    }
}
