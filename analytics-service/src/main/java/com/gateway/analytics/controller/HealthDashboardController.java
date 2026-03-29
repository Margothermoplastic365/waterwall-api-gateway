package com.gateway.analytics.controller;

import com.gateway.analytics.dto.HealthDashboardResponse;
import com.gateway.analytics.service.HealthDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/analytics/health")
@RequiredArgsConstructor
public class HealthDashboardController {

    private final HealthDashboardService healthDashboardService;

    /**
     * Full health dashboard: service checks, gateway stats, percentiles, top errors, queue depth.
     */
    @GetMapping
    public ResponseEntity<HealthDashboardResponse> getHealthDashboard() {
        return ResponseEntity.ok(healthDashboardService.getHealthDashboard());
    }

    /**
     * Detailed latency percentile breakdown for a given time range.
     */
    @GetMapping("/percentiles")
    public ResponseEntity<Map<String, Double>> getPercentiles(
            @RequestParam(defaultValue = "1h") String range) {
        return ResponseEntity.ok(healthDashboardService.getPercentiles(range));
    }
}
