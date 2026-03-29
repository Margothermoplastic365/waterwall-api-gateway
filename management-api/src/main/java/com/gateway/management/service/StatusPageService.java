package com.gateway.management.service;

import com.gateway.management.entity.MaintenanceWindowEntity;
import com.gateway.management.entity.StatusPageEntryEntity;
import com.gateway.management.repository.MaintenanceWindowRepository;
import com.gateway.management.repository.StatusPageEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages service status entries, uptime calculation, and maintenance windows
 * for the public status page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusPageService {

    private final StatusPageEntryRepository statusPageEntryRepository;
    private final MaintenanceWindowRepository maintenanceWindowRepository;

    @Transactional
    public StatusPageEntryEntity updateServiceStatus(String serviceName, String status, String message) {
        StatusPageEntryEntity entry = statusPageEntryRepository.findByServiceName(serviceName)
                .orElse(StatusPageEntryEntity.builder().serviceName(serviceName).build());

        entry.setStatus(status);
        entry.setMessage(message);

        log.info("Service status updated: service={}, status={}", serviceName, status);
        return statusPageEntryRepository.save(entry);
    }

    public List<StatusPageEntryEntity> getAllServiceStatuses() {
        return statusPageEntryRepository.findAll();
    }

    /**
     * Calculate uptime based on current statuses.
     * Returns a map of service -> uptime percentage (simplified).
     */
    public Map<String, Object> getUptimeSummary() {
        List<StatusPageEntryEntity> entries = statusPageEntryRepository.findAll();
        Map<String, Object> summary = new LinkedHashMap<>();

        long totalServices = entries.size();
        long operationalCount = entries.stream()
                .filter(e -> "OPERATIONAL".equals(e.getStatus()))
                .count();

        summary.put("totalServices", totalServices);
        summary.put("operationalCount", operationalCount);
        summary.put("overallUptimePercent", totalServices > 0
                ? Math.round((double) operationalCount / totalServices * 100.0) : 100);
        summary.put("calculatedAt", Instant.now().toString());

        return summary;
    }

    // ── Maintenance Windows ─────────────────────────────────────────────

    @Transactional
    public MaintenanceWindowEntity createMaintenanceWindow(String title, String description,
                                                            Instant startTime, Instant endTime,
                                                            String affectedServices) {
        MaintenanceWindowEntity entity = MaintenanceWindowEntity.builder()
                .title(title)
                .description(description)
                .startTime(startTime)
                .endTime(endTime)
                .affectedServices(affectedServices)
                .build();

        log.info("Maintenance window created: title={}, start={}, end={}", title, startTime, endTime);
        return maintenanceWindowRepository.save(entity);
    }

    public List<MaintenanceWindowEntity> getUpcomingMaintenance() {
        return maintenanceWindowRepository.findUpcoming(Instant.now());
    }

    public List<MaintenanceWindowEntity> getAllMaintenanceWindows() {
        return maintenanceWindowRepository.findAllByOrderByStartTimeDesc();
    }

    /**
     * Build a complete status page response including services, uptime, and maintenance.
     */
    public Map<String, Object> getStatusPage() {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("services", getAllServiceStatuses());
        page.put("uptime", getUptimeSummary());
        page.put("upcomingMaintenance", getUpcomingMaintenance());
        page.put("generatedAt", Instant.now().toString());
        return page;
    }
}
