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
public class AlertHistoryResponse {

    private Long id;
    private UUID ruleId;
    private String ruleName;
    private String status;
    private BigDecimal value;
    private String message;
    private Instant triggeredAt;
    private Instant acknowledgedAt;
    private UUID acknowledgedBy;
}
