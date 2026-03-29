package com.gateway.analytics.controller;

import com.gateway.analytics.dto.ReportConfigUpdateRequest;
import com.gateway.analytics.service.ReportSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportSchedulerService reportSchedulerService;

    @PostMapping("/trigger/{type}")
    public ResponseEntity<Map<String, String>> triggerReport(@PathVariable String type) {
        log.info("Manual report trigger requested: type={}", type);
        reportSchedulerService.triggerReport(type);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "type", type,
                "message", "Report '" + type + "' has been triggered and sent"));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(reportSchedulerService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @RequestBody ReportConfigUpdateRequest request) {
        log.info("Updating report configuration");
        reportSchedulerService.updateRecipients(
                request.getDailyRecipients(),
                request.getWeeklyRecipients(),
                request.getMonthlyRecipients());
        return ResponseEntity.ok(reportSchedulerService.getConfig());
    }
}
