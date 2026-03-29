package com.gateway.runtime.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Database-backed atomic rate limit counter for STRICT enforcement mode.
 * Uses PostgreSQL upsert with a conditional update to guarantee atomicity
 * even across multiple gateway nodes.
 */
@Slf4j
@Service
public class StrictRateLimitService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Atomic upsert: inserts a new row with count=1 if none exists for (key, window_start),
     * or increments count if below the limit. Returns the new count.
     * If the row exists but count >= limit, no update occurs and no rows are returned.
     */
    private static final String UPSERT_SQL =
            "INSERT INTO gateway.rate_limits (\"key\", window_start, \"count\", limit_value) " +
            "VALUES (?, ?, 1, ?) " +
            "ON CONFLICT (\"key\", window_start) DO UPDATE " +
            "SET \"count\" = gateway.rate_limits.\"count\" + 1 " +
            "WHERE gateway.rate_limits.\"count\" < gateway.rate_limits.limit_value " +
            "RETURNING \"count\"";

    private static final String CLEANUP_SQL =
            "DELETE FROM gateway.rate_limits WHERE window_start < ?";

    public StrictRateLimitService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically increment the rate-limit counter for the given key and time window.
     *
     * @param key           the rate-limit bucket key (e.g. "ratelimit:appId:apiId:window")
     * @param windowSeconds the size of the rate-limit window in seconds
     * @param limit         the maximum number of requests allowed per window
     * @return the current count after incrementing, or -1 if the limit has been exceeded
     */
    public int incrementAndCheck(String key, int windowSeconds, int limit) {
        long windowStart = Instant.now().getEpochSecond() / windowSeconds;
        try {
            List<Integer> results = jdbcTemplate.queryForList(UPSERT_SQL, Integer.class,
                    key, windowStart, limit);
            if (results.isEmpty()) {
                // No rows returned means the ON CONFLICT WHERE clause rejected the update
                // because count >= limit_value — rate limit exceeded
                return -1;
            }
            return results.getFirst();
        } catch (Exception e) {
            log.error("Strict rate limit check failed for key={}: {}", key, e.getMessage(), e);
            // Fail open — allow the request but log the error
            return 0;
        }
    }

    /**
     * Periodic cleanup of expired rate-limit windows.
     * Runs every 5 minutes. Deletes windows that are older than 10 minutes.
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanupExpiredWindows() {
        try {
            // Windows older than 10 minutes (expressed in epoch-seconds / 60 for 60s windows)
            long cutoff = (Instant.now().getEpochSecond() / 60) - 10;
            int deleted = jdbcTemplate.update(CLEANUP_SQL, cutoff);
            if (deleted > 0) {
                log.debug("Cleaned up {} expired rate-limit windows", deleted);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up expired rate-limit windows: {}", e.getMessage());
        }
    }
}
