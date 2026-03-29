package com.gateway.runtime.config;

import com.gateway.runtime.service.RouteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Custom health indicator that exposes gateway configuration state
 * via the /actuator/health endpoint.
 */
@Component("gatewayConfig")
@RequiredArgsConstructor
public class GatewayHealthIndicator implements HealthIndicator {

    private final RouteConfigService routeConfigService;

    @Override
    public Health health() {
        long version = routeConfigService.getConfigVersion();
        int routeCount = routeConfigService.getRouteCount();
        int planCount = routeConfigService.getPlanCount();
        int subscriptionCount = routeConfigService.getSubscriptionCount();
        Instant lastReload = routeConfigService.getLastReloadTime();

        Health.Builder builder = (version > 0) ? Health.up() : Health.down();

        builder.withDetail("configVersion", version)
               .withDetail("routeCount", routeCount)
               .withDetail("planCount", planCount)
               .withDetail("subscriptionCount", subscriptionCount);

        if (lastReload != null) {
            builder.withDetail("lastReloadTime", lastReload.toString());
        }

        return builder.build();
    }
}
