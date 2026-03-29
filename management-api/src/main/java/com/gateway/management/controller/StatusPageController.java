package com.gateway.management.controller;

import com.gateway.management.entity.MaintenanceWindowEntity;
import com.gateway.management.entity.StatusPageEntryEntity;
import com.gateway.management.service.IncidentService;
import com.gateway.management.service.StatusPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Public status page endpoints exposing service health, active incidents,
 * and upcoming maintenance windows.
 */
@RestController
@RequestMapping("/v1/status")
@RequiredArgsConstructor
public class StatusPageController {

    private final StatusPageService statusPageService;
    private final IncidentService incidentService;

    /**
     * Full status page: services, uptime, incidents, maintenance.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatusPage() {
        Map<String, Object> page = statusPageService.getStatusPage();
        page.put("activeIncidents", incidentService.listActiveIncidents());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/services")
    public ResponseEntity<List<StatusPageEntryEntity>> getServiceStatuses() {
        return ResponseEntity.ok(statusPageService.getAllServiceStatuses());
    }

    @PutMapping("/services/{serviceName}")
    public ResponseEntity<StatusPageEntryEntity> updateServiceStatus(
            @PathVariable String serviceName,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(statusPageService.updateServiceStatus(
                serviceName,
                request.getOrDefault("status", "OPERATIONAL"),
                request.get("message")
        ));
    }

    @GetMapping("/uptime")
    public ResponseEntity<Map<String, Object>> getUptime() {
        return ResponseEntity.ok(statusPageService.getUptimeSummary());
    }

    @GetMapping("/maintenance")
    public ResponseEntity<List<MaintenanceWindowEntity>> getUpcomingMaintenance() {
        return ResponseEntity.ok(statusPageService.getUpcomingMaintenance());
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceWindowEntity> createMaintenanceWindow(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(statusPageService.createMaintenanceWindow(
                        request.get("title"),
                        request.get("description"),
                        Instant.parse(request.get("startTime")),
                        Instant.parse(request.get("endTime")),
                        request.get("affectedServices")
                ));
    }
}
