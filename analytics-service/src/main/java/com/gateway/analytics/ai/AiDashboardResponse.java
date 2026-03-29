package com.gateway.analytics.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Dashboard response with aggregated AI usage statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDashboardResponse {

    private long totalTokens;
    private double totalCost;
    private long totalRequests;
    private double avgTokensPerRequest;
    private Map<String, Long> tokensByProvider;
    private Map<String, Double> costByProvider;
    private Map<String, Long> tokensByModel;
    private Map<String, Double> costByModel;
}
