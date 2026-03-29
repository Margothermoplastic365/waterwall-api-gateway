package com.gateway.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event broadcast via the {@value RabbitMQExchanges#CACHE_INVALIDATE} fanout exchange
 * to notify all nodes that a specific cache entry (or all entries in a cache) must be evicted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CacheInvalidationEvent extends BaseEvent {

    /** Logical cache name (e.g. "apiKeys", "roles", "routes"). */
    private String cacheName;

    /** The specific cache key to evict, or {@code "*"} for full cache flush. */
    private String cacheKey;

    /** Human-readable reason for the invalidation (e.g. "apikey.revoked", "role.changed"). */
    private String reason;
}
