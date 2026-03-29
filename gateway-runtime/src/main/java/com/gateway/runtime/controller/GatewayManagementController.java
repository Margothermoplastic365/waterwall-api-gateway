package com.gateway.runtime.controller;

import com.gateway.runtime.service.RouteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal management endpoints for the gateway runtime.
 * These endpoints should be secured to admin/internal access only.
 */
@Slf4j
@RestController
@RequestMapping("/v1/gateway")
@RequiredArgsConstructor
public class GatewayManagementController {

    private final RouteConfigService routeConfigService;

    /**
     * Trigger a manual config reload from the database.
     */
    @PostMapping("/config/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        log.info("Manual config refresh triggered via management endpoint");
        long start = System.currentTimeMillis();

        routeConfigService.loadAllConfig();

        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("version", routeConfigService.getConfigVersion());
        body.put("lastReload", routeConfigService.getLastReloadTime().toString());
        body.put("reloadTimeMs", elapsed);
        return ResponseEntity.ok(body);
    }

    /**
     * Return the current config version and last reload timestamp.
     */
    @GetMapping("/config/version")
    public ResponseEntity<Map<String, Object>> configVersion() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", routeConfigService.getConfigVersion());
        Instant lastReload = routeConfigService.getLastReloadTime();
        body.put("lastReload", lastReload != null ? lastReload.toString() : null);
        return ResponseEntity.ok(body);
    }

    /**
     * Detailed health information including per-component counts.
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("configVersion", routeConfigService.getConfigVersion());

        Instant lastReload = routeConfigService.getLastReloadTime();
        body.put("lastReloadTime", lastReload != null ? lastReload.toString() : null);

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("routesLoaded", routeConfigService.getRouteCount());
        components.put("plansLoaded", routeConfigService.getPlanCount());
        components.put("subscriptionsLoaded", routeConfigService.getSubscriptionCount());
        body.put("components", components);

        return ResponseEntity.ok(body);
    }
}
