package com.gateway.runtime.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks per-consumer abuse patterns including credential stuffing,
 * sequential ID enumeration, and data scraping. Computes a risk score
 * (0-100) and optionally blocks high-risk consumers.
 */
@Slf4j
@Service
public class AbuseDetectionService {

    private static final int RISK_THRESHOLD = 75;

    /** Consumer ID -> abuse metrics */
    private final ConcurrentHashMap<String, ConsumerMetrics> consumerMetrics = new ConcurrentHashMap<>();

    /**
     * Record a failed authentication attempt for a consumer.
     */
    public void recordFailedAuth(String consumerId) {
        getOrCreate(consumerId).failedAuths.incrementAndGet();
    }

    /**
     * Record a request that appears to enumerate sequential IDs.
     */
    public void recordEnumerationAttempt(String consumerId) {
        getOrCreate(consumerId).enumerationAttempts.incrementAndGet();
    }

    /**
     * Record a high-volume GET request (potential scraping).
     */
    public void recordHighVolumeGet(String consumerId) {
        getOrCreate(consumerId).highVolumeGets.incrementAndGet();
    }

    /**
     * Compute risk score for a consumer (0-100).
     */
    public int computeRiskScore(String consumerId) {
        ConsumerMetrics metrics = consumerMetrics.get(consumerId);
        if (metrics == null) {
            return 0;
        }

        int score = 0;

        // Credential stuffing: many failed auths
        int failedAuths = metrics.failedAuths.get();
        if (failedAuths > 50) score += 40;
        else if (failedAuths > 20) score += 25;
        else if (failedAuths > 5) score += 10;

        // Enumeration: sequential ID access patterns
        int enumAttempts = metrics.enumerationAttempts.get();
        if (enumAttempts > 100) score += 35;
        else if (enumAttempts > 30) score += 20;
        else if (enumAttempts > 10) score += 10;

        // Data scraping: high volume GET
        int gets = metrics.highVolumeGets.get();
        if (gets > 500) score += 25;
        else if (gets > 100) score += 15;
        else if (gets > 50) score += 5;

        return Math.min(score, 100);
    }

    /**
     * Check if a consumer should be blocked based on risk score.
     */
    public boolean shouldBlock(String consumerId) {
        return computeRiskScore(consumerId) > RISK_THRESHOLD;
    }

    /**
     * Return all consumer risk scores.
     */
    public Map<String, Integer> getAllRiskScores() {
        Map<String, Integer> scores = new ConcurrentHashMap<>();
        consumerMetrics.keySet().forEach(id -> scores.put(id, computeRiskScore(id)));
        return scores;
    }

    /**
     * Scheduled check every minute: log and alert on high-risk consumers.
     */
    @Scheduled(fixedRate = 60_000)
    public void evaluateConsumerRisks() {
        List<String> highRiskConsumers = new ArrayList<>();

        consumerMetrics.forEach((consumerId, metrics) -> {
            int score = computeRiskScore(consumerId);
            if (score > RISK_THRESHOLD) {
                highRiskConsumers.add(consumerId);
                log.warn("High risk consumer detected: consumerId={}, score={}, failedAuths={}, " +
                                "enumerationAttempts={}, highVolumeGets={}",
                        consumerId, score, metrics.failedAuths.get(),
                        metrics.enumerationAttempts.get(), metrics.highVolumeGets.get());
            }
        });

        if (!highRiskConsumers.isEmpty()) {
            log.warn("Abuse detection: {} high-risk consumers identified", highRiskConsumers.size());
        }

        // Decay old metrics to prevent indefinite accumulation
        decayMetrics();
    }

    private void decayMetrics() {
        Instant cutoff = Instant.now().minusSeconds(3600); // 1 hour decay
        consumerMetrics.entrySet().removeIf(entry -> entry.getValue().lastUpdated.isBefore(cutoff)
                && computeRiskScore(entry.getKey()) == 0);

        // Reduce counters by 10% for remaining entries
        consumerMetrics.values().forEach(metrics -> {
            decayCounter(metrics.failedAuths);
            decayCounter(metrics.enumerationAttempts);
            decayCounter(metrics.highVolumeGets);
        });
    }

    private void decayCounter(AtomicInteger counter) {
        int current = counter.get();
        if (current > 0) {
            counter.set((int) (current * 0.9));
        }
    }

    private ConsumerMetrics getOrCreate(String consumerId) {
        return consumerMetrics.computeIfAbsent(consumerId, k -> new ConsumerMetrics());
    }

    private static class ConsumerMetrics {
        final AtomicInteger failedAuths = new AtomicInteger();
        final AtomicInteger enumerationAttempts = new AtomicInteger();
        final AtomicInteger highVolumeGets = new AtomicInteger();
        volatile Instant lastUpdated = Instant.now();
    }
}
