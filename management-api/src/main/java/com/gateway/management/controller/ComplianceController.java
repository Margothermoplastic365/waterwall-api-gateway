package com.gateway.management.controller;

import com.gateway.management.entity.ComplianceReportEntity;
import com.gateway.management.entity.ConsentRecordEntity;
import com.gateway.management.entity.DataClassificationEntity;
import com.gateway.management.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    @PostMapping("/reports/generate")
    public ResponseEntity<ComplianceReportEntity> generateReport(@RequestParam String type) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.generateReport(type));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ComplianceReportEntity>> listReports() {
        return ResponseEntity.ok(complianceService.listReports());
    }

    @PostMapping("/data-classification/{apiId}")
    public ResponseEntity<DataClassificationEntity> classifyApiData(
            @PathVariable UUID apiId,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(complianceService.classifyApiData(
                apiId,
                request.getOrDefault("classification", "INTERNAL"),
                request.get("piiFields"),
                request.get("handlingPolicy")
        ));
    }

    @PostMapping("/consent")
    public ResponseEntity<ConsentRecordEntity> recordConsent(@RequestBody Map<String, Object> request) {
        UUID userId = UUID.fromString((String) request.get("userId"));
        String purpose = (String) request.get("purpose");
        boolean granted = Boolean.TRUE.equals(request.get("granted"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.recordConsent(userId, purpose, granted));
    }

    @GetMapping("/user-data/{userId}/export")
    public ResponseEntity<Map<String, Object>> exportUserData(@PathVariable UUID userId) {
        return ResponseEntity.ok(complianceService.exportUserData(userId));
    }

    @DeleteMapping("/user-data/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUserData(@PathVariable UUID userId) {
        return ResponseEntity.ok(complianceService.deleteUserData(userId));
    }
}
