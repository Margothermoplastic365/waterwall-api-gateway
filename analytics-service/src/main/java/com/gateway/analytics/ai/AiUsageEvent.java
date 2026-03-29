package com.gateway.analytics.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event representing AI usage data consumed from the message queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageEvent implements Serializable {

    private String consumerId;
    private String provider;
    private String model;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private double cost;
    private String requestId;
    private Instant timestamp;
}
