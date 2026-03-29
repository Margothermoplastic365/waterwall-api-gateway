package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiMetricsResponse {

    private UUID apiId;
    private long totalRequests;
    private long totalErrors;
    private double avgLatencyMs;
    private int maxLatencyMs;
    private double errorRate;
    private Map<String, Long> statusCodeBreakdown;
    private String timeRange;
}
