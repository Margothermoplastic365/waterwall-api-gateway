package com.gateway.runtime.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, ConcurrentHashMap-backed token bucket rate limiter.
 *
 * <p>Each bucket is identified by a composite key (typically {@code {consumerId}:{apiId}:{window}})
 * and maintains a token count that refills at a configurable rate. This implementation uses
 * atomic CAS operations for lock-free thread safety.</p>
 *
 * <p>Supports multiple time windows (per-second, per-minute, per-hour, per-day) by using
 * separate bucket keys for each window granularity.</p>
 */
@Slf4j
@Service
public class TokenBucketRateLimiter {

    /**
     * Immutable snapshot of a token bucket's state, used for atomic CAS updates.
     */
    private record BucketState(double availableTokens, long lastRefillNanos, double capacity, double refillRate) {
    }

    /**
     * Supported rate-limit time windows.
     */
    public enum Window {
        PER_SECOND("sec", 1),
        PER_MINUTE("min", 60),
        PER_HOUR("hour", 3600),
        PER_DAY("day", 86400);

        private final String suffix;
        private final int seconds;

        Window(String suffix, int seconds) {
            this.suffix = suffix;
            this.seconds = seconds;
        }

        public String getSuffix() {
            return suffix;
        }

        public int getSeconds() {
            return seconds;
        }
    }

    /**
     * Result of a consume attempt, carrying the state of the most restrictive window.
     */
    public record ConsumeResult(boolean allowed, int limit, int remaining, long resetEpochSeconds) {
    }

    private final ConcurrentHashMap<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    /**
     * Attempts to consume one or more tokens from the bucket identified by {@code key}.
     *
     * <p>The bucket is created on first access with the given {@code capacity} and
     * {@code refillRate} (tokens per second). Tokens are refilled lazily on each access
     * based on elapsed time since the last refill.</p>
     *
     * @param key        the bucket key (e.g. {@code "appId:apiId:sec"})
     * @param tokens     the number of tokens to consume
     * @param capacity   the maximum number of tokens the bucket can hold
     * @param refillRate tokens added per second
     * @return {@code true} if the tokens were successfully consumed, {@code false} if insufficient
     */
    public boolean tryConsume(String key, int tokens, double capacity, double refillRate) {
        AtomicReference<BucketState> ref = buckets.computeIfAbsent(key,
                k -> new AtomicReference<>(new BucketState(capacity, System.nanoTime(), capacity, refillRate)));

        while (true) {
            BucketState current = ref.get();
            long now = System.nanoTime();
            double elapsedSeconds = (now - current.lastRefillNanos()) / 1_000_000_000.0;

            // Refill tokens based on elapsed time
            double refilled = Math.min(capacity, current.availableTokens() + elapsedSeconds * refillRate);

            if (refilled < tokens) {
                // Not enough tokens; update refill timestamp but don't consume
                BucketState updated = new BucketState(refilled, now, capacity, refillRate);
                if (ref.compareAndSet(current, updated)) {
                    return false;
                }
                // CAS failed, retry
                continue;
            }

            // Consume tokens
            BucketState updated = new BucketState(refilled - tokens, now, capacity, refillRate);
            if (ref.compareAndSet(current, updated)) {
                return true;
            }
            // CAS failed, retry
        }
    }

    /**
     * Returns the current number of available tokens for the given bucket (after lazy refill),
     * without consuming any tokens.
     *
     * @param key      the bucket key
     * @param capacity the bucket capacity (used for refill calculation)
     * @param refillRate tokens per second
     * @return the number of available tokens, or -1 if no bucket exists
     */
    public int getAvailableTokens(String key, double capacity, double refillRate) {
        AtomicReference<BucketState> ref = buckets.get(key);
        if (ref == null) {
            return (int) capacity;
        }
        BucketState state = ref.get();
        long now = System.nanoTime();
        double elapsed = (now - state.lastRefillNanos()) / 1_000_000_000.0;
        double available = Math.min(capacity, state.availableTokens() + elapsed * refillRate);
        return (int) available;
    }

    /**
     * Computes the epoch-second at which at least one token will be available again.
     *
     * @param key      the bucket key
     * @param capacity the bucket capacity
     * @param refillRate tokens per second
     * @return reset epoch in seconds
     */
    public long getResetEpochSeconds(String key, double capacity, double refillRate) {
        AtomicReference<BucketState> ref = buckets.get(key);
        if (ref == null) {
            return System.currentTimeMillis() / 1000;
        }
        BucketState state = ref.get();
        if (state.availableTokens() >= 1.0) {
            return System.currentTimeMillis() / 1000;
        }
        // Time until one token is refilled
        double deficit = 1.0 - state.availableTokens();
        double secondsToRefill = deficit / refillRate;
        return (long) (System.currentTimeMillis() / 1000 + Math.ceil(secondsToRefill));
    }

    /**
     * Attempts to consume tokens across multiple windows simultaneously.
     * If any window is exhausted, the request is denied.
     *
     * <p>Returns a {@link ConsumeResult} reflecting the most restrictive window
     * (the one with the fewest remaining tokens relative to its capacity).</p>
     *
     * @param consumerId      the consumer/app identifier
     * @param apiId           the API identifier
     * @param windows         map of Window to its limit (requests allowed in that window)
     * @param burstAllowance  extra tokens above the base limit to allow short bursts (may be null)
     * @return the result of the consume attempt
     */
    public ConsumeResult tryConsumeAllWindows(String consumerId, String apiId,
                                               Map<Window, Integer> windows,
                                               Integer burstAllowance) {
        int burst = burstAllowance != null ? burstAllowance : 0;

        // Pre-check: see if all windows have tokens available before consuming
        // This avoids partial consumption across windows
        for (Map.Entry<Window, Integer> entry : windows.entrySet()) {
            Window window = entry.getKey();
            int limit = entry.getValue();
            double capacity = limit + burst;
            double refillRate = (double) limit / window.getSeconds();
            String key = buildKey(consumerId, apiId, window);
            int available = getAvailableTokens(key, capacity, refillRate);
            if (available < 1) {
                // This window is exhausted; build the result from it
                long resetEpoch = getResetEpochSeconds(key, capacity, refillRate);
                return new ConsumeResult(false, limit, 0, resetEpoch);
            }
        }

        // All windows have tokens; consume from each
        // Track the most restrictive window for the response headers
        int mostRestrictiveLimit = Integer.MAX_VALUE;
        int mostRestrictiveRemaining = Integer.MAX_VALUE;
        long mostRestrictiveReset = 0;
        double lowestRatio = Double.MAX_VALUE;

        for (Map.Entry<Window, Integer> entry : windows.entrySet()) {
            Window window = entry.getKey();
            int limit = entry.getValue();
            double capacity = limit + burst;
            double refillRate = (double) limit / window.getSeconds();
            String key = buildKey(consumerId, apiId, window);

            boolean consumed = tryConsume(key, 1, capacity, refillRate);
            int remaining = getAvailableTokens(key, capacity, refillRate);
            long resetEpoch = getResetEpochSeconds(key, capacity, refillRate);

            if (!consumed) {
                // Race condition: tokens disappeared between pre-check and consume
                return new ConsumeResult(false, limit, 0, resetEpoch);
            }

            // Determine the most restrictive window by remaining/capacity ratio
            double ratio = (double) remaining / capacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                mostRestrictiveLimit = limit;
                mostRestrictiveRemaining = remaining;
                mostRestrictiveReset = resetEpoch;
            }
        }

        return new ConsumeResult(true, mostRestrictiveLimit, mostRestrictiveRemaining, mostRestrictiveReset);
    }

    /**
     * Builds a bucket key in the format {@code {consumerId}:{apiId}:{windowSuffix}}.
     */
    private String buildKey(String consumerId, String apiId, Window window) {
        return consumerId + ":" + apiId + ":" + window.getSuffix();
    }

    /**
     * Returns the total number of active buckets (for monitoring).
     */
    public int size() {
        return buckets.size();
    }

    /**
     * Periodic cleanup of idle buckets that haven't been accessed recently.
     * Runs every 60 seconds. Removes buckets that are fully refilled and
     * haven't been touched in the last 5 minutes.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupIdleBuckets() {
        long now = System.nanoTime();
        long fiveMinutesNanos = 5L * 60 * 1_000_000_000L;
        int removed = 0;

        Iterator<Map.Entry<String, AtomicReference<BucketState>>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AtomicReference<BucketState>> entry = it.next();
            BucketState state = entry.getValue().get();
            long age = now - state.lastRefillNanos();
            if (age > fiveMinutesNanos) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("Token bucket cleanup: removed {} idle buckets, {} remaining", removed, buckets.size());
        }
    }
}
