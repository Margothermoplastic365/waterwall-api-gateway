package com.gateway.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaultConfig {

    private int addLatencyMs;
    private int errorRatePercent;
    private boolean simulateTimeout;
    private boolean simulateConnectionRefused;
}
