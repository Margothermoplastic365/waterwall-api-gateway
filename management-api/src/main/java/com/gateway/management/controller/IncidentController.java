package com.gateway.management.controller;

import com.gateway.management.entity.IncidentEntity;
import com.gateway.management.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<IncidentEntity> createIncident(@RequestBody Map<String, String> request) {
        UUID createdBy = request.containsKey("createdBy")
                ? UUID.fromString(request.get("createdBy")) : null;
        IncidentEntity incident = incidentService.createIncident(
                request.getOrDefault("severity", "P4"),
                request.get("title"),
                request.get("description"),
                request.get("affectedApis"),
                createdBy
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(incident);
    }

    @GetMapping
    public ResponseEntity<List<IncidentEntity>> listIncidents(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(incidentService.listActiveIncidents());
        }
        return ResponseEntity.ok(incidentService.listIncidents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentEntity> getIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<IncidentEntity> updateStatus(@PathVariable UUID id,
                                                        @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request.get("status")));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentEntity> resolveIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.resolveIncident(id));
    }
}
