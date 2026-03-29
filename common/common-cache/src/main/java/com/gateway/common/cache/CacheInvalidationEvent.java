package com.gateway.common.cache;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published via RabbitMQ fanout exchange to broadcast cache invalidation
 * across all gateway nodes. Each node that receives this event evicts the
 * specified key (or entire cache) from its local Caffeine instance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique event identifier. */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /** The Caffeine cache name to invalidate (e.g. "apiKeys", "permissions"). */
    private String cacheName;

    /** The specific cache key to evict. If null, the entire cache is cleared. */
    private String cacheKey;

    /** Human-readable reason for the invalidation (for logging / debugging). */
    private String reason;

    /** Identifier of the node that originated the invalidation. */
    private String sourceNodeId;

    /** Timestamp when the event was created. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
