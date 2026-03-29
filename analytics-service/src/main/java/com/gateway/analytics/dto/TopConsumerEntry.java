package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopConsumerEntry {

    private UUID consumerId;
    private String consumerName;
    private long requestCount;
    private long errorCount;
    private double avgLatencyMs;
}
