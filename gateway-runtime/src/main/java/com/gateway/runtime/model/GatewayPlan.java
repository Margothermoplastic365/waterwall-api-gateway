package com.gateway.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only POJO representing a cached plan definition.
 * Loaded from the gateway.plans table via JdbcTemplate.
 * Rate-limit and quota fields are parsed from the JSON columns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayPlan {

    private UUID planId;
    private String name;
    private String enforcement;       // SOFT or STRICT
    private Integer requestsPerSecond;
    private Integer requestsPerMinute;
    private Integer requestsPerDay;
    private Integer burstAllowance;
    private Long maxRequestsPerMonth;
}
