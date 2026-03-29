package com.gateway.analytics.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Cost report for a specific consumer over a given period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostReport {

    private String consumerId;
    private String period;
    private long totalTokens;
    private double totalCost;
    private long totalRequests;
    private Map<String, Double> costByModel;
    private Map<String, Long> tokensByModel;
}
