package com.gateway.analytics.controller;

import com.gateway.analytics.dto.SlaBreachResponse;
import com.gateway.analytics.dto.SlaConfigRequest;
import com.gateway.analytics.dto.SlaConfigResponse;
import com.gateway.analytics.dto.SlaDashboardEntry;
import com.gateway.analytics.service.SlaMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sla")
@RequiredArgsConstructor
public class SlaController {

    private final SlaMonitoringService slaMonitoringService;

    // ── SLA Config CRUD ─────────────────────────────────────────────────

    @PostMapping("/configs")
    public ResponseEntity<SlaConfigResponse> createConfig(@RequestBody SlaConfigRequest request) {
        SlaConfigResponse response = slaMonitoringService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/configs")
    public ResponseEntity<List<SlaConfigResponse>> listConfigs() {
        return ResponseEntity.ok(slaMonitoringService.listConfigs());
    }

    @GetMapping("/configs/{id}")
    public ResponseEntity<SlaConfigResponse> getConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(slaMonitoringService.getConfig(id));
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<SlaConfigResponse> updateConfig(@PathVariable UUID id,
                                                           @RequestBody SlaConfigRequest request) {
        return ResponseEntity.ok(slaMonitoringService.updateConfig(id, request));
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID id) {
        slaMonitoringService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    // ── Breaches ────────────────────────────────────────────────────────

    @GetMapping("/breaches")
    public ResponseEntity<List<SlaBreachResponse>> listBreaches(
            @RequestParam(required = false) UUID apiId,
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(slaMonitoringService.listBreaches(apiId, range));
    }

    // ── Dashboard ───────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<List<SlaDashboardEntry>> getDashboard() {
        return ResponseEntity.ok(slaMonitoringService.getDashboard());
    }
}
