package com.gateway.common.cache;

import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.gateway.common.events.RabbitMQExchanges;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens on the {@code cache.invalidate} fanout exchange for
 * {@link CacheInvalidationEvent} messages and evicts the specified
 * key (or entire cache) from the local Caffeine cache manager.
 *
 * <p>Each node creates an anonymous (exclusive, auto-delete) queue
 * bound to the fanout exchange so that every node receives every
 * invalidation broadcast.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final CacheManager cacheManager;

    /**
     * Receives a cache invalidation event from the fanout exchange.
     * An anonymous queue (empty {@code @Queue}) is auto-created and bound
     * to the fanout exchange, ensuring each node gets its own copy.
     */
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue,  // anonymous, exclusive, auto-delete queue
                    exchange = @Exchange(
                            name = RabbitMQExchanges.CACHE_INVALIDATE,
                            type = "fanout"
                    )
            )
    )
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        String cacheName = event.getCacheName();
        String cacheKey = event.getCacheKey();

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Received invalidation for unknown cache '{}', ignoring. eventId={}",
                    cacheName, event.getEventId());
            return;
        }

        if (cacheKey == null) {
            cache.clear();
            log.info("Cleared entire cache '{}'. reason={}, eventId={}",
                    cacheName, event.getReason(), event.getEventId());
        } else {
            cache.evict(cacheKey);
            log.debug("Evicted key '{}' from cache '{}'. reason={}, eventId={}",
                    cacheKey, cacheName, event.getReason(), event.getEventId());
        }
    }
}
