package com.gateway.analytics.controller;

import com.gateway.analytics.dto.ApiComparisonEntry;
import com.gateway.analytics.dto.ApiMetricsResponse;
import com.gateway.analytics.dto.DashboardResponse;
import com.gateway.analytics.dto.TopApiEntry;
import com.gateway.analytics.dto.TopConsumerEntry;
import com.gateway.analytics.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/analytics/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(dashboardService.getDashboard(range));
    }

    @GetMapping("/api/{apiId}")
    public ResponseEntity<ApiMetricsResponse> getApiMetrics(
            @PathVariable UUID apiId,
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(dashboardService.getApiMetrics(apiId, range));
    }

    @GetMapping("/top-apis")
    public ResponseEntity<List<TopApiEntry>> getTopApis(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "requests") String metric,
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(dashboardService.getTopApis(limit, metric, range));
    }

    @GetMapping("/top-consumers")
    public ResponseEntity<List<TopConsumerEntry>> getTopConsumers(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(dashboardService.getTopConsumers(limit, range));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "24h") String range) {
        String csv = dashboardService.exportCsv(range);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dashboard-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/compare")
    public ResponseEntity<List<ApiComparisonEntry>> compareApis(
            @RequestParam List<UUID> apiIds,
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(dashboardService.compareApis(apiIds, range));
    }
}
