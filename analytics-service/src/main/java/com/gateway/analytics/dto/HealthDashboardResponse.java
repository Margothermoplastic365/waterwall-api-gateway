package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthDashboardResponse {

    private List<ServiceHealth> services;
    private GatewayStats gateway;
    private Map<String, Double> percentiles;
    private List<TopError> topErrors;
    private long queueDepth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealth {
        private String name;
        private String url;
        private String status;
        private long latencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayStats {
        private double currentRps;
        private double errorRate;
        private double avgLatencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopError {
        private int statusCode;
        private long count;
        private Instant lastSeen;
    }
}
