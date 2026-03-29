package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaConfigResponse {

    private UUID id;
    private UUID apiId;
    private String apiName;
    private BigDecimal uptimeTarget;
    private Integer latencyTargetMs;
    private BigDecimal errorBudgetPct;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
