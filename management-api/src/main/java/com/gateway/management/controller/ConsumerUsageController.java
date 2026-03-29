package com.gateway.management.controller;

import com.gateway.management.dto.ApiUsageBreakdownResponse;
import com.gateway.management.dto.CostEstimateResponse;
import com.gateway.management.dto.UsageHistoryResponse;
import com.gateway.management.dto.UsageSummaryResponse;
import com.gateway.management.service.ConsumerUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/consumer/usage")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConsumerUsageController {

    private final ConsumerUsageService consumerUsageService;

    /**
     * Returns a usage summary for the authenticated consumer including
     * request counts (today / this week / this month), average latency,
     * error rate, active subscription count, and top APIs by request volume.
     */
    @GetMapping("/summary")
    public ResponseEntity<UsageSummaryResponse> getUsageSummary() {
        return ResponseEntity.ok(consumerUsageService.getUsageSummary());
    }

    /**
     * Returns daily usage history for the authenticated consumer.
     * The {@code range} parameter controls the look-back window (e.g. "7d", "30d", "90d").
     * Defaults to 7 days when omitted.
     */
    @GetMapping("/history")
    public ResponseEntity<UsageHistoryResponse> getUsageHistory(
            @RequestParam(defaultValue = "7d") String range) {
        return ResponseEntity.ok(consumerUsageService.getUsageHistory(range));
    }

    /**
     * Returns a per-API breakdown of requests, latency, and errors
     * for the current billing month.
     */
    @GetMapping("/apis")
    public ResponseEntity<ApiUsageBreakdownResponse> getApiBreakdown() {
        return ResponseEntity.ok(consumerUsageService.getApiBreakdown());
    }

    /**
     * Returns a cost estimate for the current billing period based on
     * actual usage and the consumer's active pricing plan.
     */
    @GetMapping("/cost")
    public ResponseEntity<CostEstimateResponse> getCostEstimate() {
        return ResponseEntity.ok(consumerUsageService.getCostEstimate());
    }
}
