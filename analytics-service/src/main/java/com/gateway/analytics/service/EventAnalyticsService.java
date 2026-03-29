package com.gateway.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks event-specific analytics: throughput per topic, consumer lag,
 * and event processing latency.
 *
 * <p>Metrics are collected in-memory and can be persisted / queried.
 * In a production system this would integrate with a time-series database.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    /** topic → counter for throughput tracking */
    private final ConcurrentHashMap<String, AtomicLong> throughputCounters = new ConcurrentHashMap<>();

    /** topic → last event timestamp for recency tracking */
    private final ConcurrentHashMap<String, Instant> lastEventTimestamps = new ConcurrentHashMap<>();

    /** subscriptionId → lag (number of un-consumed messages) */
    private final ConcurrentHashMap<String, AtomicLong> consumerLag = new ConcurrentHashMap<>();

    /** topic → cumulative latency sum (ms) and count for average calculation */
    private final ConcurrentHashMap<String, long[]> latencyAccumulators = new ConcurrentHashMap<>();

    // ── Recording ────────────────────────────────────────────────────────

    /**
     * Record that an event was produced/consumed on a topic.
     */
    public void recordEvent(String topic) {
        throughputCounters.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
        lastEventTimestamps.put(topic, Instant.now());
    }

    /**
     * Record processing latency for a specific event.
     */
    public void recordLatency(String topic, long latencyMs) {
        latencyAccumulators.compute(topic, (k, v) -> {
            if (v == null) v = new long[]{0L, 0L};
            v[0] += latencyMs;  // sum
            v[1] += 1;          // count
            return v;
        });
    }

    /**
     * Update consumer lag for a subscription.
     */
    public void updateConsumerLag(String subscriptionId, long lag) {
        consumerLag.computeIfAbsent(subscriptionId, k -> new AtomicLong()).set(lag);
    }

    // ── Querying ─────────────────────────────────────────────────────────

    /**
     * Get throughput (total event count) for a topic.
     */
    public Map<String, Object> getThroughput(String topic) {
        AtomicLong counter = throughputCounters.get(topic);
        long count = counter != null ? counter.get() : 0;
        Instant lastEvent = lastEventTimestamps.get(topic);

        return Map.of(
                "topic", topic,
                "totalEvents", count,
                "lastEventAt", lastEvent != null ? lastEvent.toString() : "never"
        );
    }

    /**
     * Get throughput for all topics.
     */
    public List<Map<String, Object>> getAllThroughput() {
        return throughputCounters.keySet().stream()
                .map(this::getThroughput)
                .toList();
    }

    /**
     * Get consumer lag for all subscriptions.
     */
    public List<Map<String, Object>> getConsumerLag() {
        return consumerLag.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "subscriptionId", entry.getKey(),
                        "lag", entry.getValue().get()
                ))
                .toList();
    }

    /**
     * Get latency statistics for all topics.
     */
    public List<Map<String, Object>> getLatencyStats() {
        return latencyAccumulators.entrySet().stream()
                .map(entry -> {
                    long[] acc = entry.getValue();
                    long sum = acc[0];
                    long count = acc[1];
                    double avg = count > 0 ? (double) sum / count : 0.0;
                    return Map.<String, Object>of(
                            "topic", entry.getKey(),
                            "totalEvents", count,
                            "totalLatencyMs", sum,
                            "avgLatencyMs", Math.round(avg * 100.0) / 100.0
                    );
                })
                .toList();
    }
}
