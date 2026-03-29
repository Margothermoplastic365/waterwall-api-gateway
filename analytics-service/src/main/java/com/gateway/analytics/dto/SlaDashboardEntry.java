package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaDashboardEntry {

    private UUID apiId;
    private String apiName;
    private BigDecimal uptimeTarget;
    private BigDecimal uptimeActual;
    private boolean uptimeCompliant;
    private Integer latencyTargetMs;
    private BigDecimal latencyP95Actual;
    private boolean latencyCompliant;
    private BigDecimal errorBudgetPct;
    private BigDecimal errorRateActual;
    private boolean errorRateCompliant;
    private boolean overallCompliant;
    private int activeBreaches;
}
