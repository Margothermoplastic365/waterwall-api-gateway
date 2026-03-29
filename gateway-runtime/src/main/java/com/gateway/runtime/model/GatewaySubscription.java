package com.gateway.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only POJO representing a cached subscription.
 * Loaded from the gateway.subscriptions table via JdbcTemplate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewaySubscription {

    private UUID subscriptionId;
    private UUID applicationId;
    private UUID apiId;
    private UUID planId;
    private String status;
    private String environmentSlug;
}
