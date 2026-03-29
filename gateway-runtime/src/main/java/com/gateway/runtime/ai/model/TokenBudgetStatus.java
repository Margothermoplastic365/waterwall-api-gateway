package com.gateway.runtime.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token budget status for a consumer showing usage against limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBudgetStatus {

    private String consumerId;
    private long dailyLimit;
    private long dailyUsed;
    private long monthlyLimit;
    private long monthlyUsed;
    private double estimatedCost;
}
