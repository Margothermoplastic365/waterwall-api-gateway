package com.gateway.analytics.controller;

import com.gateway.analytics.ai.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI observability and usage dashboards.
 */
@RestController
@RequestMapping("/v1/analytics/ai")
@RequiredArgsConstructor
public class AiDashboardController {

    private final AiDashboardService aiDashboardService;

    /**
     * AI usage dashboard for the specified time range.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AiDashboardResponse> getDashboard(
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(aiDashboardService.getDashboard(range));
    }

    /**
     * Top token consumers.
     */
    @GetMapping("/top-consumers")
    public ResponseEntity<List<AiConsumerUsage>> getTopConsumers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(aiDashboardService.getTopConsumers(limit));
    }

    /**
     * Cost report for a specific consumer.
     */
    @GetMapping("/cost/{consumerId}")
    public ResponseEntity<CostReport> getCostReport(
            @PathVariable String consumerId,
            @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(aiDashboardService.getCostReport(consumerId, period));
    }

    /**
     * Model usage breakdown across all consumers.
     */
    @GetMapping("/models")
    public ResponseEntity<List<Map<String, Object>>> getModelUsage() {
        return ResponseEntity.ok(aiDashboardService.getModelUsage());
    }
}
