package com.gateway.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event broadcast via the {@value RabbitMQExchanges#RATELIMIT_SYNC} fanout exchange
 * to synchronise rate-limit counters across all gateway nodes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RateLimitSyncEvent extends BaseEvent {

    /** The rate-limit bucket key (e.g. "apikey:abc123:per-minute"). */
    private String key;

    /** The counter increment to apply. */
    private int increment;

    /** Identifier of the gateway node that originated this increment. */
    private String nodeId;
}
