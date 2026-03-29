package com.gateway.analytics.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI usage breakdown for a specific consumer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsumerUsage {

    private String consumerId;
    private long totalTokens;
    private double totalCost;
    private long totalRequests;
}
