package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsStreamEvent {

    private Instant timestamp;
    private double currentRps;
    private double errorRate;
    private double avgLatencyMs;
    private double p99LatencyMs;
    private List<String> activeAlerts;
}
