package com.gateway.analytics.controller;

import com.gateway.analytics.dto.AlertHistoryResponse;
import com.gateway.analytics.dto.AlertRuleRequest;
import com.gateway.analytics.dto.AlertRuleResponse;
import com.gateway.analytics.service.AlertingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertingService alertingService;

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleResponse> createRule(@RequestBody AlertRuleRequest request) {
        AlertRuleResponse response = alertingService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<AlertRuleResponse>> listRules() {
        return ResponseEntity.ok(alertingService.listRules());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<AlertRuleResponse> updateRule(@PathVariable UUID id,
                                                         @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(alertingService.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertingService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<Page<AlertHistoryResponse>> getAlertHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(alertingService.getAlertHistory(page, size, status));
    }

    @PostMapping("/history/{id}/acknowledge")
    public ResponseEntity<AlertHistoryResponse> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam(required = false) UUID acknowledgedBy) {
        return ResponseEntity.ok(alertingService.acknowledgeAlert(id, acknowledgedBy));
    }
}
