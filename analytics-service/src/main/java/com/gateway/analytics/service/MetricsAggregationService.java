package com.gateway.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically aggregates request_logs into rollup metrics tables:
 * <ul>
 *   <li>Every minute: request_logs → metrics_1m</li>
 *   <li>Every hour: metrics_1m → metrics_1h</li>
 *   <li>Every day: metrics_1h → metrics_1d</li>
 * </ul>
 */
@Slf4j
@Service
public class MetricsAggregationService {

    private final JdbcTemplate jdbcTemplate;

    public MetricsAggregationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Aggregate request_logs into metrics_1m every minute.
     * Uses INSERT ... SELECT with ON CONFLICT to be idempotent.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void aggregateToMinute() {
        log.debug("Starting minute-level metrics aggregation");
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO analytics.metrics_1m (api_id, window_start, request_count, error_count,
                    latency_sum_ms, latency_max_ms, bytes_in, bytes_out)
                SELECT
                    api_id,
                    date_trunc('minute', created_at) AS window_start,
                    COUNT(*) AS request_count,
                    COUNT(*) FILTER (WHERE status_code >= 400) AS error_count,
                    COALESCE(SUM(latency_ms), 0) AS latency_sum_ms,
                    COALESCE(MAX(latency_ms), 0) AS latency_max_ms,
                    COALESCE(SUM(request_size), 0) AS bytes_in,
                    COALESCE(SUM(response_size), 0) AS bytes_out
                FROM analytics.request_logs
                WHERE created_at >= date_trunc('minute', NOW() - INTERVAL '2 minutes')
                  AND created_at < date_trunc('minute', NOW())
                GROUP BY api_id, date_trunc('minute', created_at)
                ON CONFLICT ON CONSTRAINT uq_metrics1m
                DO UPDATE SET
                    request_count = EXCLUDED.request_count,
                    error_count = EXCLUDED.error_count,
                    latency_sum_ms = EXCLUDED.latency_sum_ms,
                    latency_max_ms = EXCLUDED.latency_max_ms,
                    bytes_in = EXCLUDED.bytes_in,
                    bytes_out = EXCLUDED.bytes_out
                """);
            log.debug("Minute aggregation complete: {} rows upserted", rows);
        } catch (Exception e) {
            log.error("Failed minute-level metrics aggregation: {}", e.getMessage(), e);
        }
    }

    /**
     * Roll up metrics_1m into metrics_1h every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void aggregateToHour() {
        log.debug("Starting hour-level metrics aggregation");
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO analytics.metrics_1h (api_id, window_start, request_count, error_count,
                    latency_sum_ms, latency_max_ms)
                SELECT
                    api_id,
                    date_trunc('hour', window_start) AS window_start,
                    SUM(request_count) AS request_count,
                    SUM(error_count) AS error_count,
                    SUM(latency_sum_ms) AS latency_sum_ms,
                    MAX(latency_max_ms) AS latency_max_ms
                FROM analytics.metrics_1m
                WHERE window_start >= date_trunc('hour', NOW() - INTERVAL '2 hours')
                  AND window_start < date_trunc('hour', NOW())
                GROUP BY api_id, date_trunc('hour', window_start)
                ON CONFLICT ON CONSTRAINT uq_metrics1h
                DO UPDATE SET
                    request_count = EXCLUDED.request_count,
                    error_count = EXCLUDED.error_count,
                    latency_sum_ms = EXCLUDED.latency_sum_ms,
                    latency_max_ms = EXCLUDED.latency_max_ms
                """);
            log.debug("Hour aggregation complete: {} rows upserted", rows);
        } catch (Exception e) {
            log.error("Failed hour-level metrics aggregation: {}", e.getMessage(), e);
        }
    }

    /**
     * Roll up metrics_1h into metrics_1d every day at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void aggregateToDay() {
        log.debug("Starting day-level metrics aggregation");
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO analytics.metrics_1d (api_id, window_start, request_count, error_count,
                    latency_sum_ms, latency_max_ms)
                SELECT
                    api_id,
                    date_trunc('day', window_start)::date AS window_start,
                    SUM(request_count) AS request_count,
                    SUM(error_count) AS error_count,
                    SUM(latency_sum_ms) AS latency_sum_ms,
                    MAX(latency_max_ms) AS latency_max_ms
                FROM analytics.metrics_1h
                WHERE window_start >= date_trunc('day', NOW() - INTERVAL '2 days')
                  AND window_start < date_trunc('day', NOW())
                GROUP BY api_id, date_trunc('day', window_start)
                ON CONFLICT ON CONSTRAINT uq_metrics1d
                DO UPDATE SET
                    request_count = EXCLUDED.request_count,
                    error_count = EXCLUDED.error_count,
                    latency_sum_ms = EXCLUDED.latency_sum_ms,
                    latency_max_ms = EXCLUDED.latency_max_ms
                """);
            log.debug("Day aggregation complete: {} rows upserted", rows);
        } catch (Exception e) {
            log.error("Failed day-level metrics aggregation: {}", e.getMessage(), e);
        }
    }
}
