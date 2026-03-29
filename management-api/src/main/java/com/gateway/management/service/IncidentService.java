package com.gateway.management.service;

import com.gateway.management.entity.IncidentEntity;
import com.gateway.management.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages incident lifecycle: creation, investigation, resolution.
 * Supports auto-creation from critical alerts and severity classification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;

    @Transactional
    public IncidentEntity createIncident(String severity, String title, String description,
                                          String affectedApis, UUID createdBy) {
        IncidentEntity incident = IncidentEntity.builder()
                .severity(severity)
                .title(title)
                .description(description)
                .status("OPEN")
                .affectedApis(affectedApis)
                .createdBy(createdBy)
                .build();

        IncidentEntity saved = incidentRepository.save(incident);
        log.info("Incident created: id={}, severity={}, title={}", saved.getId(), severity, title);
        return saved;
    }

    /**
     * Auto-create an incident from a critical alert.
     */
    @Transactional
    public IncidentEntity createFromAlert(String alertTitle, String alertDescription, String affectedApis) {
        String severity = classifySeverity(alertTitle, alertDescription);
        return createIncident(severity, "[AUTO] " + alertTitle, alertDescription, affectedApis, null);
    }

    public List<IncidentEntity> listIncidents() {
        return incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<IncidentEntity> listActiveIncidents() {
        return incidentRepository.findByStatusInOrderByCreatedAtDesc(
                List.of("OPEN", "INVESTIGATING"));
    }

    public IncidentEntity getIncident(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
    }

    @Transactional
    public IncidentEntity updateStatus(UUID id, String status) {
        IncidentEntity incident = getIncident(id);
        incident.setStatus(status);
        if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
            incident.setResolvedAt(Instant.now());
        }
        log.info("Incident status updated: id={}, status={}", id, status);
        return incidentRepository.save(incident);
    }

    @Transactional
    public IncidentEntity resolveIncident(UUID id) {
        return updateStatus(id, "RESOLVED");
    }

    /**
     * Classify severity based on keywords in alert content.
     */
    private String classifySeverity(String title, String description) {
        String combined = (title + " " + description).toLowerCase();
        if (combined.contains("down") || combined.contains("outage") || combined.contains("critical")) {
            return "P1";
        }
        if (combined.contains("degraded") || combined.contains("high latency") || combined.contains("error rate")) {
            return "P2";
        }
        if (combined.contains("warning") || combined.contains("elevated")) {
            return "P3";
        }
        return "P4";
    }
}
