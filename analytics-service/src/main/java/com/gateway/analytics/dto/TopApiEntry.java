package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopApiEntry {

    private UUID apiId;
    private String apiName;
    private long requestCount;
    private long errorCount;
    private double avgLatencyMs;
    private double errorRate;
}
