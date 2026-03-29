package com.gateway.analytics.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service providing AI usage analytics dashboards, top-consumer reports,
 * and per-consumer cost breakdowns.
 */
@Slf4j
@Service
public class AiDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public AiDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Aggregate AI usage dashboard for the given time range.
     */
    public AiDashboardResponse getDashboard(String timeRange) {
        String interval = toInterval(timeRange);

        // Summary stats
        String summarySql = """
            SELECT
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COALESCE(SUM(cost), 0) AS total_cost,
                COUNT(*) AS total_requests,
                COALESCE(AVG(total_tokens), 0) AS avg_tokens
            FROM gateway.ai_token_usage
            WHERE created_at >= NOW() - INTERVAL '%s'
            """.formatted(interval);

        Map<String, Object> summary = jdbcTemplate.queryForMap(summarySql);

        // Per-provider breakdown
        String providerSql = """
            SELECT provider,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM gateway.ai_token_usage
            WHERE created_at >= NOW() - INTERVAL '%s'
            GROUP BY provider
            ORDER BY tokens DESC
            """.formatted(interval);

        Map<String, Long> tokensByProvider = new LinkedHashMap<>();
        Map<String, Double> costByProvider = new LinkedHashMap<>();
        jdbcTemplate.query(providerSql, (rs, rowNum) -> {
            tokensByProvider.put(rs.getString("provider"), rs.getLong("tokens"));
            costByProvider.put(rs.getString("provider"), rs.getDouble("cost"));
            return null;
        });

        // Per-model breakdown
        String modelSql = """
            SELECT model,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM gateway.ai_token_usage
            WHERE created_at >= NOW() - INTERVAL '%s'
            GROUP BY model
            ORDER BY tokens DESC
            """.formatted(interval);

        Map<String, Long> tokensByModel = new LinkedHashMap<>();
        Map<String, Double> costByModel = new LinkedHashMap<>();
        jdbcTemplate.query(modelSql, (rs, rowNum) -> {
            tokensByModel.put(rs.getString("model"), rs.getLong("tokens"));
            costByModel.put(rs.getString("model"), rs.getDouble("cost"));
            return null;
        });

        return AiDashboardResponse.builder()
                .totalTokens(((Number) summary.get("total_tokens")).longValue())
                .totalCost(((Number) summary.get("total_cost")).doubleValue())
                .totalRequests(((Number) summary.get("total_requests")).longValue())
                .avgTokensPerRequest(((Number) summary.get("avg_tokens")).doubleValue())
                .tokensByProvider(tokensByProvider)
                .costByProvider(costByProvider)
                .tokensByModel(tokensByModel)
                .costByModel(costByModel)
                .build();
    }

    /**
     * Get the top AI token consumers.
     */
    public List<AiConsumerUsage> getTopConsumers(int limit) {
        String sql = """
            SELECT consumer_id,
                   COALESCE(SUM(total_tokens), 0) AS total_tokens,
                   COALESCE(SUM(cost), 0) AS total_cost,
                   COUNT(*) AS total_requests
            FROM gateway.ai_token_usage
            WHERE consumer_id IS NOT NULL
            GROUP BY consumer_id
            ORDER BY total_tokens DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> AiConsumerUsage.builder()
                .consumerId(rs.getString("consumer_id"))
                .totalTokens(rs.getLong("total_tokens"))
                .totalCost(rs.getDouble("total_cost"))
                .totalRequests(rs.getLong("total_requests"))
                .build(), limit);
    }

    /**
     * Cost report for a specific consumer over a given period.
     */
    public CostReport getCostReport(String consumerId, String period) {
        String interval = toInterval(period);

        String sql = """
            SELECT
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COALESCE(SUM(cost), 0) AS total_cost,
                COUNT(*) AS total_requests
            FROM gateway.ai_token_usage
            WHERE consumer_id = ?
              AND created_at >= NOW() - INTERVAL '%s'
            """.formatted(interval);

        Map<String, Object> stats = jdbcTemplate.queryForMap(sql, consumerId);

        String modelSql = """
            SELECT model,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(total_tokens), 0) AS tokens
            FROM gateway.ai_token_usage
            WHERE consumer_id = ?
              AND created_at >= NOW() - INTERVAL '%s'
            GROUP BY model
            ORDER BY cost DESC
            """.formatted(interval);

        Map<String, Double> costByModel = new LinkedHashMap<>();
        Map<String, Long> tokensByModel = new LinkedHashMap<>();
        jdbcTemplate.query(modelSql, (rs, rowNum) -> {
            costByModel.put(rs.getString("model"), rs.getDouble("cost"));
            tokensByModel.put(rs.getString("model"), rs.getLong("tokens"));
            return null;
        }, consumerId);

        return CostReport.builder()
                .consumerId(consumerId)
                .period(period)
                .totalTokens(((Number) stats.get("total_tokens")).longValue())
                .totalCost(((Number) stats.get("total_cost")).doubleValue())
                .totalRequests(((Number) stats.get("total_requests")).longValue())
                .costByModel(costByModel)
                .tokensByModel(tokensByModel)
                .build();
    }

    /**
     * Model usage breakdown (all consumers).
     */
    public List<Map<String, Object>> getModelUsage() {
        String sql = """
            SELECT model, provider,
                   COUNT(*) AS request_count,
                   COALESCE(SUM(total_tokens), 0) AS total_tokens,
                   COALESCE(SUM(cost), 0) AS total_cost,
                   COALESCE(AVG(total_tokens), 0) AS avg_tokens
            FROM gateway.ai_token_usage
            GROUP BY model, provider
            ORDER BY total_tokens DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("model", rs.getString("model"));
            row.put("provider", rs.getString("provider"));
            row.put("requestCount", rs.getLong("request_count"));
            row.put("totalTokens", rs.getLong("total_tokens"));
            row.put("totalCost", rs.getDouble("total_cost"));
            row.put("avgTokens", rs.getDouble("avg_tokens"));
            return row;
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String toInterval(String timeRange) {
        if (timeRange == null) return "24 hours";
        return switch (timeRange.toLowerCase()) {
            case "1h" -> "1 hour";
            case "6h" -> "6 hours";
            case "24h" -> "24 hours";
            case "7d" -> "7 days";
            case "30d" -> "30 days";
            case "monthly" -> "30 days";
            case "weekly" -> "7 days";
            case "daily" -> "24 hours";
            default -> "24 hours";
        };
    }
}
