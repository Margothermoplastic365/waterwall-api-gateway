package com.gateway.analytics.service;

import com.gateway.analytics.dto.ApiComparisonEntry;
import com.gateway.analytics.dto.ApiMetricsResponse;
import com.gateway.analytics.dto.DashboardResponse;
import com.gateway.analytics.dto.TopApiEntry;
import com.gateway.analytics.dto.TopConsumerEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Platform-wide dashboard stats for the given time range.
     */
    public DashboardResponse getDashboard(String timeRange) {
        String interval = toInterval(timeRange);

        // Use request_logs for real-time data, or metrics tables for historical
        String sql = """
            SELECT
                COUNT(*) AS total_requests,
                COALESCE(AVG(latency_ms), 0) AS avg_latency,
                COALESCE(
                    COUNT(*) FILTER (WHERE status_code >= 400) * 100.0 / NULLIF(COUNT(*), 0),
                    0
                ) AS error_rate,
                COUNT(DISTINCT api_id) AS active_apis
            FROM analytics.request_logs
            WHERE created_at >= NOW() - INTERVAL '%s'
            """.formatted(interval);

        Map<String, Object> stats = jdbcTemplate.queryForMap(sql);

        long totalRequests = ((Number) stats.get("total_requests")).longValue();
        double avgLatency = ((Number) stats.get("avg_latency")).doubleValue();
        double errorRate = ((Number) stats.get("error_rate")).doubleValue();
        long activeApis = ((Number) stats.get("active_apis")).longValue();

        // Status code breakdown
        String breakdownSql = """
            SELECT
                CAST(status_code / 100 AS TEXT) || 'xx' AS status_group,
                COUNT(*) AS cnt
            FROM analytics.request_logs
            WHERE created_at >= NOW() - INTERVAL '%s'
            GROUP BY status_code / 100
            ORDER BY status_group
            """.formatted(interval);

        Map<String, Long> statusCodeBreakdown = new LinkedHashMap<>();
        jdbcTemplate.query(breakdownSql, (rs, rowNum) -> {
            statusCodeBreakdown.put(rs.getString("status_group"), rs.getLong("cnt"));
            return null;
        });

        double roundedLatency = Math.round(avgLatency * 100.0) / 100.0;
        return DashboardResponse.builder()
                .totalRequests(totalRequests)
                .avgLatencyMs(roundedLatency)
                .avgLatency(roundedLatency)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .activeApis(activeApis)
                .statusCodeBreakdown(statusCodeBreakdown)
                .build();
    }

    /**
     * Metrics for a specific API.
     */
    public ApiMetricsResponse getApiMetrics(UUID apiId, String timeRange) {
        String interval = toInterval(timeRange);

        String sql = """
            SELECT
                COUNT(*) AS total_requests,
                COUNT(*) FILTER (WHERE status_code >= 400) AS total_errors,
                COALESCE(AVG(latency_ms), 0) AS avg_latency,
                COALESCE(MAX(latency_ms), 0) AS max_latency,
                COALESCE(
                    COUNT(*) FILTER (WHERE status_code >= 400) * 100.0 / NULLIF(COUNT(*), 0),
                    0
                ) AS error_rate
            FROM analytics.request_logs
            WHERE api_id = ?
              AND created_at >= NOW() - INTERVAL '%s'
            """.formatted(interval);

        Map<String, Object> stats = jdbcTemplate.queryForMap(sql, apiId);

        // Status code breakdown for this API
        String breakdownSql = """
            SELECT
                CAST(status_code / 100 AS TEXT) || 'xx' AS status_group,
                COUNT(*) AS cnt
            FROM analytics.request_logs
            WHERE api_id = ?
              AND created_at >= NOW() - INTERVAL '%s'
            GROUP BY status_code / 100
            ORDER BY status_group
            """.formatted(interval);

        Map<String, Long> statusCodeBreakdown = new LinkedHashMap<>();
        jdbcTemplate.query(breakdownSql, (rs, rowNum) -> {
            statusCodeBreakdown.put(rs.getString("status_group"), rs.getLong("cnt"));
            return null;
        }, apiId);

        return ApiMetricsResponse.builder()
                .apiId(apiId)
                .totalRequests(((Number) stats.get("total_requests")).longValue())
                .totalErrors(((Number) stats.get("total_errors")).longValue())
                .avgLatencyMs(Math.round(((Number) stats.get("avg_latency")).doubleValue() * 100.0) / 100.0)
                .maxLatencyMs(((Number) stats.get("max_latency")).intValue())
                .errorRate(Math.round(((Number) stats.get("error_rate")).doubleValue() * 100.0) / 100.0)
                .statusCodeBreakdown(statusCodeBreakdown)
                .timeRange(timeRange)
                .build();
    }

    /**
     * Top APIs by the given metric.
     */
    public List<TopApiEntry> getTopApis(int limit, String metric, String timeRange) {
        String interval = toInterval(timeRange);
        String orderBy = switch (metric != null ? metric.toLowerCase() : "requests") {
            case "errors" -> "error_count DESC";
            case "latency" -> "avg_latency DESC";
            case "error_rate" -> "error_rate DESC";
            default -> "request_count DESC";
        };

        String sql = """
            SELECT
                r.api_id,
                COALESCE(a.name, CAST(r.api_id AS text)) AS api_name,
                COUNT(*) AS request_count,
                COUNT(*) FILTER (WHERE r.status_code >= 400) AS error_count,
                COALESCE(AVG(r.latency_ms), 0) AS avg_latency,
                COALESCE(
                    COUNT(*) FILTER (WHERE r.status_code >= 400) * 100.0 / NULLIF(COUNT(*), 0),
                    0
                ) AS error_rate
            FROM analytics.request_logs r
            LEFT JOIN gateway.apis a ON a.id = r.api_id
            WHERE r.created_at >= NOW() - INTERVAL '%s'
            GROUP BY r.api_id, a.name
            ORDER BY %s
            LIMIT ?
            """.formatted(interval, orderBy);

        return jdbcTemplate.query(sql, (rs, rowNum) -> TopApiEntry.builder()
                .apiId(rs.getObject("api_id", UUID.class))
                .apiName(rs.getString("api_name"))
                .requestCount(rs.getLong("request_count"))
                .errorCount(rs.getLong("error_count"))
                .avgLatencyMs(Math.round(rs.getDouble("avg_latency") * 100.0) / 100.0)
                .errorRate(Math.round(rs.getDouble("error_rate") * 100.0) / 100.0)
                .build(), limit);
    }

    /**
     * Top consumers by request count.
     */
    public List<TopConsumerEntry> getTopConsumers(int limit, String timeRange) {
        String interval = toInterval(timeRange);

        String sql = """
            SELECT
                r.consumer_id,
                COALESCE(u.email, CAST(r.consumer_id AS text)) AS consumer_name,
                COUNT(*) AS request_count,
                COUNT(*) FILTER (WHERE r.status_code >= 400) AS error_count,
                COALESCE(AVG(r.latency_ms), 0) AS avg_latency
            FROM analytics.request_logs r
            LEFT JOIN identity.users u ON u.id = r.consumer_id
            WHERE r.consumer_id IS NOT NULL
              AND r.created_at >= NOW() - INTERVAL '%s'
            GROUP BY r.consumer_id, u.email
            ORDER BY request_count DESC
            LIMIT ?
            """.formatted(interval);

        return jdbcTemplate.query(sql, (rs, rowNum) -> TopConsumerEntry.builder()
                .consumerId(rs.getObject("consumer_id", UUID.class))
                .consumerName(rs.getString("consumer_name"))
                .requestCount(rs.getLong("request_count"))
                .errorCount(rs.getLong("error_count"))
                .avgLatencyMs(Math.round(rs.getDouble("avg_latency") * 100.0) / 100.0)
                .build(), limit);
    }

    /**
     * Export dashboard data as CSV string.
     */
    public String exportCsv(String timeRange) {
        DashboardResponse dashboard = getDashboard(timeRange);
        List<TopApiEntry> topApis = getTopApis(50, "requests", timeRange);

        StringBuilder csv = new StringBuilder();
        csv.append("# Platform Dashboard Export\n");
        csv.append("# Time Range: ").append(timeRange).append("\n\n");

        csv.append("# Summary\n");
        csv.append("total_requests,avg_latency_ms,error_rate\n");
        csv.append(dashboard.getTotalRequests()).append(",")
                .append(dashboard.getAvgLatencyMs()).append(",")
                .append(dashboard.getErrorRate()).append("\n\n");

        csv.append("# Top APIs\n");
        csv.append("api_id,request_count,error_count,avg_latency_ms,error_rate\n");
        for (TopApiEntry entry : topApis) {
            csv.append(entry.getApiId()).append(",")
                    .append(entry.getRequestCount()).append(",")
                    .append(entry.getErrorCount()).append(",")
                    .append(entry.getAvgLatencyMs()).append(",")
                    .append(entry.getErrorRate()).append("\n");
        }

        return csv.toString();
    }

    /**
     * Latency percentiles (p50, p75, p90, p95, p99) from request_logs for the given range.
     */
    public Map<String, Double> getPercentiles(String timeRange) {
        String interval = toInterval(timeRange);

        String sql = """
            SELECT
                PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY latency_ms) AS p50,
                PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY latency_ms) AS p75,
                PERCENTILE_CONT(0.9)  WITHIN GROUP (ORDER BY latency_ms) AS p90,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95,
                PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99
            FROM analytics.request_logs
            WHERE created_at >= NOW() - INTERVAL '%s'
            """.formatted(interval);

        Map<String, Object> row = jdbcTemplate.queryForMap(sql);

        Map<String, Double> percentiles = new LinkedHashMap<>();
        percentiles.put("p50", toDouble(row.get("p50")));
        percentiles.put("p75", toDouble(row.get("p75")));
        percentiles.put("p90", toDouble(row.get("p90")));
        percentiles.put("p95", toDouble(row.get("p95")));
        percentiles.put("p99", toDouble(row.get("p99")));
        return percentiles;
    }

    /**
     * Compare multiple APIs side by side.
     */
    public List<ApiComparisonEntry> compareApis(List<UUID> apiIds, String timeRange) {
        if (apiIds == null || apiIds.isEmpty()) {
            return List.of();
        }

        String interval = toInterval(timeRange);
        double intervalSeconds = toSeconds(timeRange);

        // Build IN clause with positional parameters
        String placeholders = String.join(",", apiIds.stream().map(id -> "?").toList());

        String sql = """
            SELECT
                api_id,
                COUNT(*) AS total_requests,
                COALESCE(AVG(latency_ms), 0) AS avg_latency,
                COALESCE(
                    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms),
                    0
                ) AS p50_latency,
                COALESCE(
                    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms),
                    0
                ) AS p95_latency,
                COALESCE(
                    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms),
                    0
                ) AS p99_latency,
                COALESCE(
                    COUNT(*) FILTER (WHERE status_code >= 400) * 100.0 / NULLIF(COUNT(*), 0),
                    0
                ) AS error_rate
            FROM analytics.request_logs
            WHERE api_id IN (%s)
              AND created_at >= NOW() - INTERVAL '%s'
            GROUP BY api_id
            ORDER BY total_requests DESC
            """.formatted(placeholders, interval);

        Object[] params = apiIds.toArray();

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long totalRequests = rs.getLong("total_requests");
            double throughputRps = intervalSeconds > 0 ? totalRequests / intervalSeconds : 0;

            return ApiComparisonEntry.builder()
                    .apiId(rs.getObject("api_id", UUID.class))
                    .apiName(null) // API name not stored in request_logs; enrich externally if needed
                    .totalRequests(totalRequests)
                    .avgLatencyMs(Math.round(rs.getDouble("avg_latency") * 100.0) / 100.0)
                    .p50LatencyMs(Math.round(rs.getDouble("p50_latency") * 100.0) / 100.0)
                    .p95LatencyMs(Math.round(rs.getDouble("p95_latency") * 100.0) / 100.0)
                    .p99LatencyMs(Math.round(rs.getDouble("p99_latency") * 100.0) / 100.0)
                    .errorRate(Math.round(rs.getDouble("error_rate") * 100.0) / 100.0)
                    .throughputRps(Math.round(throughputRps * 100.0) / 100.0)
                    .build();
        }, params);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        return Math.round(((Number) value).doubleValue() * 100.0) / 100.0;
    }

    private String toInterval(String timeRange) {
        if (timeRange == null) return "24 hours";
        return switch (timeRange.toLowerCase()) {
            case "1h" -> "1 hour";
            case "6h" -> "6 hours";
            case "24h" -> "24 hours";
            case "7d" -> "7 days";
            case "30d" -> "30 days";
            default -> "24 hours";
        };
    }

    private double toSeconds(String timeRange) {
        if (timeRange == null) return 86400;
        return switch (timeRange.toLowerCase()) {
            case "1h" -> 3600;
            case "6h" -> 21600;
            case "24h" -> 86400;
            case "7d" -> 604800;
            case "30d" -> 2592000;
            default -> 86400;
        };
    }
}
