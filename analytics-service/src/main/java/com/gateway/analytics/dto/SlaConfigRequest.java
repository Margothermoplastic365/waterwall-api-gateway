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
public class SlaConfigRequest {

    private UUID apiId;
    private String apiName;
    private BigDecimal uptimeTarget;
    private Integer latencyTargetMs;
    private BigDecimal errorBudgetPct;
    private Boolean enabled;
}
