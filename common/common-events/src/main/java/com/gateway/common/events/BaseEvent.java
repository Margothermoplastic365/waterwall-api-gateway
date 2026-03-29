package com.gateway.common.events;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Abstract base class for every event published on the platform event bus.
 * Provides common envelope fields (id, type, timestamp, actor, trace).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEvent {

    /** Unique event identifier (UUID v4). */
    private String eventId;

    /** Discriminator used for routing / deserialization (e.g. "cache.invalidate"). */
    private String eventType;

    /** Moment the event was created. */
    private Instant timestamp;

    /** Identity of the user or system component that triggered the event. */
    private String actorId;

    /** Distributed trace id for correlation across services. */
    private String traceId;

    /**
     * Convenience constructor that auto-generates {@code eventId} and {@code timestamp}.
     *
     * @param eventType the event type discriminator
     * @param actorId   the actor that caused the event
     * @param traceId   the distributed trace id
     */
    protected BaseEvent(String eventType, String actorId, String traceId) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.eventType = eventType;
        this.actorId = actorId;
        this.traceId = traceId;
    }
}
