package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostEstimateResponse {

    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private long totalRequests;
    private long includedRequests;
    private long overageRequests;
    private BigDecimal estimatedCost;
    private String currency;
    private String pricingModel;
    private String planName;
}
