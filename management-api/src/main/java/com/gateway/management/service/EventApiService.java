package com.gateway.management.service;

import com.gateway.management.dto.CreateEventApiRequest;
import com.gateway.management.dto.EventApiResponse;
import com.gateway.management.entity.EventApiEntity;
import com.gateway.management.entity.EventSubscriptionEntity;
import com.gateway.management.repository.EventApiRepository;
import com.gateway.management.repository.EventSubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for event APIs (async / event-driven APIs backed by a message broker).
 * Also manages consumer subscriptions to event APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventApiService {

    private final EventApiRepository eventApiRepository;
    private final EventSubscriptionRepository eventSubscriptionRepository;

    // ── CRUD ─────────────────────────────────────────────────────────────

    @Transactional
    public EventApiResponse createEventApi(CreateEventApiRequest request) {
        EventApiEntity entity = EventApiEntity.builder()
                .name(request.getName())
                .protocol(request.getProtocol().toUpperCase())
                .connectionConfig(request.getConnectionConfig())
                .topics(request.getTopics())
                .schemaConfig(request.getSchemaConfig())
                .build();
        entity = eventApiRepository.save(entity);
        log.info("Created event API: id={} name={} protocol={}", entity.getId(), entity.getName(), entity.getProtocol());
        return toResponse(entity);
    }

    public List<EventApiResponse> listEventApis() {
        return eventApiRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public EventApiResponse getEventApi(UUID id) {
        EventApiEntity entity = findOrThrow(id);
        return toResponse(entity);
    }

    @Transactional
    public EventApiResponse updateEventApi(UUID id, CreateEventApiRequest request) {
        EventApiEntity entity = findOrThrow(id);
        entity.setName(request.getName());
        entity.setProtocol(request.getProtocol().toUpperCase());
        entity.setConnectionConfig(request.getConnectionConfig());
        entity.setTopics(request.getTopics());
        entity.setSchemaConfig(request.getSchemaConfig());
        entity = eventApiRepository.save(entity);
        log.info("Updated event API: id={}", id);
        return toResponse(entity);
    }

    @Transactional
    public void deleteEventApi(UUID id) {
        if (!eventApiRepository.existsById(id)) {
            throw new EntityNotFoundException("Event API not found: " + id);
        }
        eventApiRepository.deleteById(id);
        log.info("Deleted event API: id={}", id);
    }

    // ── Subscriptions ────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> subscribeConsumer(UUID eventApiId, UUID consumerId, String topic) {
        EventApiEntity eventApi = findOrThrow(eventApiId);

        EventSubscriptionEntity sub = EventSubscriptionEntity.builder()
                .eventApi(eventApi)
                .consumerId(consumerId)
                .topic(topic)
                .status("ACTIVE")
                .build();
        sub = eventSubscriptionRepository.save(sub);

        log.info("Consumer {} subscribed to event API {} topic={}", consumerId, eventApiId, topic);
        return Map.of(
                "subscriptionId", sub.getId(),
                "eventApiId", eventApiId,
                "consumerId", consumerId,
                "topic", topic,
                "status", sub.getStatus()
        );
    }

    public List<Map<String, Object>> listSubscriptions(UUID eventApiId) {
        return eventSubscriptionRepository.findByEventApiId(eventApiId).stream()
                .map(sub -> Map.<String, Object>of(
                        "subscriptionId", sub.getId(),
                        "consumerId", sub.getConsumerId() != null ? sub.getConsumerId() : "",
                        "topic", sub.getTopic() != null ? sub.getTopic() : "",
                        "status", sub.getStatus(),
                        "createdAt", sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : ""
                ))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private EventApiEntity findOrThrow(UUID id) {
        return eventApiRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event API not found: " + id));
    }

    private EventApiResponse toResponse(EventApiEntity entity) {
        return EventApiResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .protocol(entity.getProtocol())
                .connectionConfig(entity.getConnectionConfig())
                .topics(entity.getTopics())
                .schemaConfig(entity.getSchemaConfig())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
