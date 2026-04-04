package com.gateway.common.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<BaseEvent> eventCaptor;

    // ── Generic publish ────────────────────────────────────────────────

    @Test
    void shouldPublishToTopicExchange() {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .eventType("cache.invalidate")
                .cacheKey("routes:all")
                .reason("manual flush")
                .build();

        eventPublisher.publish("platform.events", "cache.invalidate", event);

        verify(rabbitTemplate).convertAndSend(
                eq("platform.events"),
                eq("cache.invalidate"),
                eq(event));
    }

    @Test
    void shouldPublishToFanoutExchange() {
        RateLimitSyncEvent event = RateLimitSyncEvent.builder()
                .eventType("ratelimit.sync")
                .key("apikey:abc123:per-minute")
                .increment(1)
                .nodeId("node-1")
                .build();

        eventPublisher.publishToFanout("ratelimit.sync", event);

        verify(rabbitTemplate).convertAndSend(
                eq("ratelimit.sync"),
                eq(""),
                eq(event));
    }

    // ── Domain-specific shortcuts ──────────────────────────────────────

    @Test
    void shouldPublishCacheInvalidation() {
        eventPublisher.publishCacheInvalidation("routes:all", "route updated");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQExchanges.CACHE_INVALIDATE),
                eq(""),
                eventCaptor.capture());

        BaseEvent captured = eventCaptor.getValue();
        assertThat(captured).isInstanceOf(CacheInvalidationEvent.class);

        CacheInvalidationEvent cacheEvent = (CacheInvalidationEvent) captured;
        assertThat(cacheEvent.getEventType()).isEqualTo("cache.invalidate");
        assertThat(cacheEvent.getCacheKey()).isEqualTo("routes:all");
        assertThat(cacheEvent.getReason()).isEqualTo("route updated");
    }

    @Test
    void shouldPublishRateLimitSync() {
        eventPublisher.publishRateLimitSync("apikey:xyz:per-minute", 5, "node-42");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQExchanges.RATELIMIT_SYNC),
                eq(""),
                eventCaptor.capture());

        BaseEvent captured = eventCaptor.getValue();
        assertThat(captured).isInstanceOf(RateLimitSyncEvent.class);

        RateLimitSyncEvent rlEvent = (RateLimitSyncEvent) captured;
        assertThat(rlEvent.getEventType()).isEqualTo("ratelimit.sync");
        assertThat(rlEvent.getKey()).isEqualTo("apikey:xyz:per-minute");
        assertThat(rlEvent.getIncrement()).isEqualTo(5);
        assertThat(rlEvent.getNodeId()).isEqualTo("node-42");
    }

    @Test
    void shouldPublishConfigRefresh() {
        eventPublisher.publishConfigRefresh();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQExchanges.CONFIG_REFRESH),
                eq(""),
                eventCaptor.capture());

        BaseEvent captured = eventCaptor.getValue();
        assertThat(captured.getEventType()).isEqualTo("config.refresh");
        assertThat(captured.getActorId()).isEqualTo("system");
        assertThat(captured.getEventId()).isNotNull();
        assertThat(captured.getTimestamp()).isNotNull();
    }
}
