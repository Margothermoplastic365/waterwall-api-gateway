package com.gateway.management.controller;

import com.gateway.management.entity.ApiChangelogEntity;
import com.gateway.management.service.ChangelogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API changelog management endpoints.
 * Generates version diffs, tracks breaking changes, and produces migration guides.
 */
@RestController
@RequestMapping("/v1/changelogs")
@RequiredArgsConstructor
public class ChangelogController {

    private final ChangelogService changelogService;

    /**
     * List all changelogs for an API.
     */
    @GetMapping("/{apiId}")
    public ResponseEntity<List<Map<String, Object>>> listChangelogs(@PathVariable UUID apiId) {
        List<ApiChangelogEntity> changelogs = changelogService.listChangelogs(apiId);
        List<Map<String, Object>> response = changelogs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Generate a changelog between two versions of an API.
     */
    @PostMapping("/{apiId}/generate")
    public ResponseEntity<Map<String, Object>> generateChangelog(
            @PathVariable UUID apiId,
            @RequestParam String from,
            @RequestParam String to) {
        ApiChangelogEntity changelog = changelogService.generateChangelog(apiId, from, to);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(changelog));
    }

    /**
     * Get breaking changes for a specific version of an API.
     */
    @GetMapping("/{apiId}/breaking-changes")
    public ResponseEntity<List<Map<String, Object>>> getBreakingChanges(
            @PathVariable UUID apiId,
            @RequestParam String version) {
        List<Map<String, Object>> breakingChanges = changelogService.getBreakingChanges(apiId, version);
        return ResponseEntity.ok(breakingChanges);
    }

    /**
     * Get migration guide between two versions.
     */
    @GetMapping("/{apiId}/migration-guide")
    public ResponseEntity<Map<String, Object>> getMigrationGuide(
            @PathVariable UUID apiId,
            @RequestParam String from,
            @RequestParam String to) {
        String guide = changelogService.generateMigrationGuide(apiId, from, to);
        return ResponseEntity.ok(Map.of(
                "apiId", apiId,
                "fromVersion", from,
                "toVersion", to,
                "migrationGuide", guide
        ));
    }

    private Map<String, Object> toResponse(ApiChangelogEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "apiId", entity.getApiId(),
                "versionFrom", entity.getVersionFrom() != null ? entity.getVersionFrom() : "",
                "versionTo", entity.getVersionTo() != null ? entity.getVersionTo() : "",
                "changes", entity.getChanges() != null ? entity.getChanges() : "[]",
                "breakingChanges", entity.getBreakingChanges() != null ? entity.getBreakingChanges() : "[]",
                "migrationGuide", entity.getMigrationGuide() != null ? entity.getMigrationGuide() : "",
                "createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : ""
        );
    }
}
