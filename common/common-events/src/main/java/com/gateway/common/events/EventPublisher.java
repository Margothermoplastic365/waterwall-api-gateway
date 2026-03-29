package com.gateway.common.events;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Convenience helper for publishing {@link BaseEvent} instances to RabbitMQ.
 * Wraps {@link RabbitTemplate} and provides typed shortcut methods for the
 * most common exchange patterns used across the platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    // ── Generic publish ─────────────────────────────────────────────────

    /**
     * Publish an event to a topic exchange with the given routing key.
     */
    public void publish(String exchange, String routingKey, BaseEvent event) {
        log.debug("Publishing event [{}] to exchange={} routingKey={}",
                event.getEventType(), exchange, routingKey);
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }

    /**
     * Publish an event to a fanout exchange (routing key is ignored).
     */
    public void publishToFanout(String exchange, BaseEvent event) {
        log.debug("Publishing fanout event [{}] to exchange={}",
                event.getEventType(), exchange);
        rabbitTemplate.convertAndSend(exchange, "", event);
    }

    // ── Domain-specific shortcuts ───────────────────────────────────────

    /**
     * Broadcast a cache invalidation event to all nodes.
     *
     * @param cacheKey the key (or pattern) to invalidate
     * @param reason   human-readable reason for the invalidation
     */
    public void publishCacheInvalidation(String cacheKey, String reason) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .eventType("cache.invalidate")
                .cacheKey(cacheKey)
                .reason(reason)
                .build();
        publishToFanout(RabbitMQExchanges.CACHE_INVALIDATE, event);
    }

    /**
     * Broadcast a rate-limit counter increment to all gateway nodes.
     *
     * @param key       the rate-limit bucket key
     * @param increment the counter increment value
     * @param nodeId    identifier of the originating node
     */
    public void publishRateLimitSync(String key, int increment, String nodeId) {
        RateLimitSyncEvent event = RateLimitSyncEvent.builder()
                .eventType("ratelimit.sync")
                .key(key)
                .increment(increment)
                .nodeId(nodeId)
                .build();
        publishToFanout(RabbitMQExchanges.RATELIMIT_SYNC, event);
    }

    /**
     * Broadcast a configuration refresh signal to all nodes.
     */
    public void publishConfigRefresh() {
        BaseEvent event = new ConfigRefreshEvent();
        publishToFanout(RabbitMQExchanges.CONFIG_REFRESH, event);
    }

    // ── Inner event used only for config refresh ────────────────────────

    /**
     * Lightweight event that signals all nodes to reload configuration.
     */
    @lombok.Data
    @lombok.experimental.SuperBuilder
    @lombok.EqualsAndHashCode(callSuper = true)
    private static class ConfigRefreshEvent extends BaseEvent {
        ConfigRefreshEvent() {
            super("config.refresh", "system", resolveHostName());
        }

        private static String resolveHostName() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return "unknown";
            }
        }
    }
}
