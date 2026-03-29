package com.gateway.common.cache;

import java.net.InetAddress;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.gateway.common.events.RabbitMQExchanges;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes {@link CacheInvalidationEvent} messages to the
 * {@code cache.invalidate} fanout exchange so that all gateway nodes
 * evict stale entries from their local Caffeine caches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String NODE_ID = resolveNodeId();

    /**
     * Publishes an invalidation event for a specific key in the given cache.
     *
     * @param cacheName the Caffeine cache name (see {@link CacheNames})
     * @param cacheKey  the specific key to evict from all nodes
     * @param reason    human-readable reason for the invalidation
     */
    public void invalidate(String cacheName, String cacheKey, String reason) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .cacheName(cacheName)
                .cacheKey(cacheKey)
                .reason(reason)
                .sourceNodeId(NODE_ID)
                .build();

        rabbitTemplate.convertAndSend(RabbitMQExchanges.CACHE_INVALIDATE, "", event);
        log.debug("Published cache invalidation: cache={}, key={}, reason={}, eventId={}",
                cacheName, cacheKey, reason, event.getEventId());
    }

    /**
     * Publishes an invalidation event that clears the entire cache on all nodes.
     *
     * @param cacheName the Caffeine cache name to clear entirely
     */
    public void invalidateAll(String cacheName) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .cacheName(cacheName)
                .cacheKey(null)
                .reason("Full cache clear requested")
                .sourceNodeId(NODE_ID)
                .build();

        rabbitTemplate.convertAndSend(RabbitMQExchanges.CACHE_INVALIDATE, "", event);
        log.info("Published full cache invalidation: cache={}, eventId={}",
                cacheName, event.getEventId());
    }

    private static String resolveNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-" + ProcessHandle.current().pid();
        }
    }
}
