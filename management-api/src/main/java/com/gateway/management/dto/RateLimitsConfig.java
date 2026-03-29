package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitsConfig {

    private Integer requestsPerSecond;
    private Integer requestsPerMinute;
    private Integer requestsPerDay;
    private Integer burstAllowance;
}
