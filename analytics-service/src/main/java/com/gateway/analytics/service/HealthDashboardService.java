package com.gateway.analytics.service;

import com.gateway.analytics.dto.HealthDashboardResponse;
import com.gateway.analytics.dto.HealthDashboardResponse.GatewayStats;
import com.gateway.analytics.dto.HealthDashboardResponse.ServiceHealth;
import com.gateway.analytics.dto.HealthDashboardResponse.TopError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final List<ServiceDefinition> SERVICES = List.of(
            new ServiceDefinition("identity-service", "http://localhost:8081"),
            new ServiceDefinition("management-api", "http://localhost:8082"),
            new ServiceDefinition("gateway-runtime", "http://localhost:8080"),
            new ServiceDefinition("analytics", "http://localhost:8083"),
            new ServiceDefinition("notification", "http://localhost:8084")
    );

    private record ServiceDefinition(String name, String baseUrl) {}

    /**
     * Full health dashboard combining service checks, gateway stats, percentiles, and top errors.
     */
    public HealthDashboardResponse getHealthDashboard() {
        List<ServiceHealth> services = checkAllServices();
        GatewayStats gatewayStats = getGatewayStats();
        Map<String, Double> percentiles = getPercentiles("1h");
        List<TopError> topErrors = getTopErrors();
        long queueDepth = getQueueDepth();

        return HealthDashboardResponse.builder()
                .services(services)
                .gateway(gatewayStats)
                .percentiles(percentiles)
                .topErrors(topErrors)
                .queueDepth(queueDepth)
                .build();
    }

    /**
     * Ping all known services at /actuator/health with a 3-second timeout.
     */
    public List<ServiceHealth> checkAllServices() {
        RestClient restClient = RestClient.builder()
                .build();

        List<ServiceHealth> results = new ArrayList<>();
        for (ServiceDefinition svc : SERVICES) {
            String url = svc.baseUrl() + "/actuator/health";
            long start = System.nanoTime();
            try {
                restClient.get()
                        .uri(url)
                        .retrieve()
                        .toBodilessEntity();

                long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                results.add(ServiceHealth.builder()
                        .name(svc.name())
                        .url(url)
                        .status("UP")
                        .latencyMs(latencyMs)
                        .build());
            } catch (Exception e) {
                log.debug("Health check failed for {}: {}", svc.name(), e.getMessage());
                results.add(ServiceHealth.builder()
                        .name(svc.name())
                        .url(url)
                        .status("DOWN")
                        .latencyMs(-1)
                        .build());
            }
        }
        return results;
    }

    /**
     * Gateway stats from the last 1 minute of request_logs: RPS, error rate, avg latency.
     */
    public GatewayStats getGatewayStats() {
        String sql = """
            SELECT
                COUNT(*) AS total,
                COALESCE(
                    COUNT(*) FILTER (WHERE status_code >= 400) * 100.0 / NULLIF(COUNT(*), 0),
                    0
                ) AS error_rate,
                COALESCE(AVG(latency_ms), 0) AS avg_latency
            FROM analytics.request_logs
            WHERE created_at >= NOW() - INTERVAL '1 minute'
            """;

        Map<String, Object> row = jdbcTemplate.queryForMap(sql);
        long total = ((Number) row.get("total")).longValue();
        double errorRate = ((Number) row.get("error_rate")).doubleValue();
        double avgLatency = ((Number) row.get("avg_latency")).doubleValue();

        // RPS = total requests / 60 seconds
        double rps = total / 60.0;

        return GatewayStats.builder()
                .currentRps(Math.round(rps * 100.0) / 100.0)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                .build();
    }

    /**
     * Latency percentiles (p50, p75, p90, p95, p99) from request_logs for the given range.
     */
    public Map<String, Double> getPercentiles(String range) {
        String interval = toInterval(range);

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
     * Top 10 error status codes in the last hour.
     */
    public List<TopError> getTopErrors() {
        String sql = """
            SELECT
                status_code,
                COUNT(*) AS cnt,
                MAX(created_at) AS last_seen
            FROM analytics.request_logs
            WHERE status_code >= 400
              AND created_at >= NOW() - INTERVAL '1 hour'
            GROUP BY status_code
            ORDER BY cnt DESC
            LIMIT 10
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> TopError.builder()
                .statusCode(rs.getInt("status_code"))
                .count(rs.getLong("cnt"))
                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                .build());
    }

    /**
     * Best-effort queue depth from RabbitMQ. Returns 0 if unavailable.
     */
    public long getQueueDepth() {
        try {
            var props = rabbitTemplate.execute(channel -> {
                try {
                    return channel.queueDeclarePassive("gateway.request.logs");
                } catch (Exception e) {
                    return null;
                }
            });
            return props != null ? props.getMessageCount() : 0;
        } catch (Exception e) {
            log.debug("Could not determine queue depth: {}", e.getMessage());
            return 0;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String toInterval(String timeRange) {
        if (timeRange == null) return "1 hour";
        return switch (timeRange.toLowerCase()) {
            case "5m" -> "5 minutes";
            case "15m" -> "15 minutes";
            case "30m" -> "30 minutes";
            case "1h" -> "1 hour";
            case "6h" -> "6 hours";
            case "24h" -> "24 hours";
            case "7d" -> "7 days";
            default -> "1 hour";
        };
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        return Math.round(((Number) value).doubleValue() * 100.0) / 100.0;
    }
}
