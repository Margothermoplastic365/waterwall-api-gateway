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
public class SlaBreachResponse {

    private UUID id;
    private UUID apiId;
    private String apiName;
    private UUID slaConfigId;
    private String metric;
    private BigDecimal targetValue;
    private BigDecimal actualValue;
    private String message;
    private Instant breachedAt;
    private Instant resolvedAt;
}
