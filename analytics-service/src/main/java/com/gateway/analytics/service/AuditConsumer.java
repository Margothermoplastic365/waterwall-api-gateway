package com.gateway.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.analytics.entity.AuditEventEntity;
import com.gateway.analytics.repository.AuditEventRepository;
import com.gateway.common.events.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Consumes {@link AuditEvent} messages from the {@code audit.events} topic exchange
 * and persists them in the centralized {@code analytics.audit_events_all} table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumer {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "analytics-service.audit-events", durable = "true"),
            exchange = @Exchange(value = "audit.events", type = "topic"),
            key = "audit.#"
    ))
    public void handleAuditEvent(org.springframework.amqp.core.Message rawMessage) {
        try {
            AuditEvent event = objectMapper.readValue(rawMessage.getBody(), AuditEvent.class);
            log.debug("Received audit event: eventId={} action={} resource={}:{}",
                    event.getEventId(), event.getAction(),
                    event.getResourceType(), event.getResourceId());

            AuditEventEntity entity = AuditEventEntity.builder()
                    .eventId(event.getEventId())
                    .actorId(parseUuid(event.getActorId()))
                    .actorEmail(event.getActorEmail())
                    .actorIp(event.getActorIp())
                    .action(event.getAction())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .beforeState(serializeState(event.getBeforeState()))
                    .afterState(serializeState(event.getAfterState()))
                    .result(event.getResult())
                    .traceId(event.getTraceId())
                    .sourceService(resolveSourceService(event))
                    .build();

            auditEventRepository.save(entity);
            log.debug("Persisted audit event: id={} action={}", entity.getId(), entity.getAction());
        } catch (Exception ex) {
            log.error("Failed to process audit event: {}", ex.getMessage(), ex);
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit state: {}", e.getMessage());
            return state.toString();
        }
    }

    private String resolveSourceService(AuditEvent event) {
        // Derive source service from the event type prefix if available
        String eventType = event.getEventType();
        if (eventType != null && eventType.startsWith("audit.")) {
            String remainder = eventType.substring(6);
            int dot = remainder.indexOf('.');
            if (dot > 0) {
                return remainder.substring(0, dot);
            }
        }
        return "unknown";
    }
}
