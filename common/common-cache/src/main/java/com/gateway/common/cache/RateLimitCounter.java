package com.gateway.common.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe, in-memory rate-limit counter backed by {@link ConcurrentHashMap}
 * with {@link AtomicInteger} values.
 *
 * <p>This is intentionally <b>not</b> a Caffeine cache because rate limiting
 * requires atomic increment-and-check semantics that the Spring Cache / Caffeine
 * {@code Cache} abstraction does not expose.</p>
 *
 * <p>Keys follow the convention {@code "consumerId:windowId"} where
 * {@code windowId} encodes the time window (e.g. epoch-second / window-size).
 * A scheduled task purges stale windows every 60 seconds.</p>
 *
 * <p>Cross-node synchronisation happens via the {@code ratelimit.sync}
 * RabbitMQ fanout exchange; the gateway runtime calls
 * {@link #applyRemoteIncrement(String, int)} when a sync message arrives.</p>
 */
@Slf4j
@EnableScheduling
public class RateLimitCounter {

    /** Key = "consumerId:windowId", Value = current request count in this window. */
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Atomically increments the counter for the given key and checks the limit.
     *
     * @param key   the rate-limit key (e.g. {@code "app-123:1711324800"})
     * @param limit the maximum allowed requests for this window
     * @return the new count after increment, or {@code -1} if the limit
     *         has already been reached (request should be rejected)
     */
    public int incrementAndGet(String key, int limit) {
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int newValue = counter.incrementAndGet();
        if (newValue > limit) {
            // Already over limit — don't bother decrementing; the counter
            // tracks actual attempts, and the window will be cleaned up.
            return -1;
        }
        return newValue;
    }

    /**
     * Returns the current count for the given key without modifying it.
     *
     * @param key the rate-limit key
     * @return the current count, or 0 if no counter exists
     */
    public int get(String key) {
        AtomicInteger counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Removes the counter for the given key (e.g. when a window expires).
     *
     * @param key the rate-limit key to remove
     */
    public void reset(String key) {
        counters.remove(key);
    }

    /**
     * Applies a remote increment received via RabbitMQ fanout from another
     * gateway node. This is used for soft rate-limit synchronisation.
     *
     * @param key       the rate-limit key
     * @param increment the number of requests to add from the remote node
     */
    public void applyRemoteIncrement(String key, int increment) {
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        counter.addAndGet(increment);
        log.trace("Applied remote increment: key={}, increment={}, newTotal={}",
                key, increment, counter.get());
    }

    /**
     * Returns the total number of active counter entries (for monitoring).
     */
    public int size() {
        return counters.size();
    }

    /**
     * Scheduled cleanup that removes expired time-window entries every 60 seconds.
     *
     * <p>A window key is considered expired if its epoch-second portion
     * (the segment after the last ':') represents a time more than 120 seconds
     * in the past. This gives a generous buffer beyond any practical window size.</p>
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredWindows() {
        long cutoff = System.currentTimeMillis() / 1000 - 120;
        int removed = 0;

        Iterator<Map.Entry<String, AtomicInteger>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AtomicInteger> entry = it.next();
            String key = entry.getKey();
            int lastColon = key.lastIndexOf(':');
            if (lastColon >= 0) {
                try {
                    long windowEpoch = Long.parseLong(key.substring(lastColon + 1));
                    if (windowEpoch < cutoff) {
                        it.remove();
                        removed++;
                    }
                } catch (NumberFormatException e) {
                    // Non-numeric window suffix — skip, don't remove
                }
            }
        }

        if (removed > 0) {
            log.debug("Rate-limit cleanup: removed {} expired windows, {} remaining",
                    removed, counters.size());
        }
    }
}
