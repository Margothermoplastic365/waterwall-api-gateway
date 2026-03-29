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
public class AlertRuleRequest {

    private String name;
    private String metric;
    private String condition;
    private BigDecimal threshold;
    private int windowMinutes;
    private UUID apiId;
    private boolean enabled;
    private String channels;
}
