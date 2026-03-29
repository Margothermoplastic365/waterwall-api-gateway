package com.gateway.analytics.controller;

import com.gateway.analytics.entity.AuditEventEntity;
import com.gateway.analytics.repository.AuditEventRepository;
import com.gateway.common.auth.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API for querying and exporting centralized audit events.
 * Requires the {@code audit:read} permission.
 */
@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    /**
     * Search audit events with optional filters and pagination.
     */
    @GetMapping
    @RequiresPermission("audit:read")
    public ResponseEntity<Page<AuditEventEntity>> searchAuditEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID actorUuid = actorId != null ? UUID.fromString(actorId) : null;

        Page<AuditEventEntity> result = auditEventRepository.search(
                actorUuid, action, resourceType, from, to, pageable);

        return ResponseEntity.ok(result);
    }

    /**
     * Export audit events as CSV or JSON.
     */
    @GetMapping("/export")
    @RequiresPermission("audit:read")
    public ResponseEntity<String> exportAuditEvents(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        UUID actorUuid = actorId != null ? UUID.fromString(actorId) : null;

        List<AuditEventEntity> events = auditEventRepository.searchForExport(
                actorUuid, action, resourceType, from, to);

        if ("json".equalsIgnoreCase(format)) {
            return exportAsJson(events);
        }
        return exportAsCsv(events);
    }

    private ResponseEntity<String> exportAsCsv(List<AuditEventEntity> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,event_id,actor_id,actor_email,actor_ip,action,resource_type,resource_id,result,trace_id,source_service,created_at\n");

        for (AuditEventEntity e : events) {
            csv.append(e.getId()).append(',')
               .append(escapeCsv(e.getEventId())).append(',')
               .append(e.getActorId() != null ? e.getActorId() : "").append(',')
               .append(escapeCsv(e.getActorEmail())).append(',')
               .append(escapeCsv(e.getActorIp())).append(',')
               .append(escapeCsv(e.getAction())).append(',')
               .append(escapeCsv(e.getResourceType())).append(',')
               .append(escapeCsv(e.getResourceId())).append(',')
               .append(escapeCsv(e.getResult())).append(',')
               .append(escapeCsv(e.getTraceId())).append(',')
               .append(escapeCsv(e.getSourceService())).append(',')
               .append(e.getCreatedAt()).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private ResponseEntity<String> exportAsJson(List<AuditEventEntity> events) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (int i = 0; i < events.size(); i++) {
            AuditEventEntity e = events.get(i);
            if (i > 0) json.append(',');
            json.append('{')
                .append("\"id\":").append(e.getId())
                .append(",\"eventId\":\"").append(nullSafe(e.getEventId())).append('"')
                .append(",\"actorId\":\"").append(e.getActorId() != null ? e.getActorId() : "").append('"')
                .append(",\"actorEmail\":\"").append(nullSafe(e.getActorEmail())).append('"')
                .append(",\"actorIp\":\"").append(nullSafe(e.getActorIp())).append('"')
                .append(",\"action\":\"").append(nullSafe(e.getAction())).append('"')
                .append(",\"resourceType\":\"").append(nullSafe(e.getResourceType())).append('"')
                .append(",\"resourceId\":\"").append(nullSafe(e.getResourceId())).append('"')
                .append(",\"result\":\"").append(nullSafe(e.getResult())).append('"')
                .append(",\"traceId\":\"").append(nullSafe(e.getTraceId())).append('"')
                .append(",\"sourceService\":\"").append(nullSafe(e.getSourceService())).append('"')
                .append(",\"createdAt\":\"").append(e.getCreatedAt()).append('"')
                .append('}');
        }
        json.append(']');

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.toString());
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String nullSafe(String value) {
        return value != null ? value.replace("\"", "\\\"") : "";
    }
}
