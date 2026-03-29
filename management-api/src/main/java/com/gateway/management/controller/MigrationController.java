package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.MigrationRequest;
import com.gateway.management.dto.MigrationResponse;
import com.gateway.management.service.MigrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/migrations")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping
    @RequiresPermission("environment:promote")
    public ResponseEntity<MigrationResponse> initiateMigration(
            @Valid @RequestBody MigrationRequest request) {
        MigrationResponse response = migrationService.initiateMigration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MigrationResponse> getMigration(@PathVariable UUID id) {
        return ResponseEntity.ok(migrationService.getMigration(id));
    }

    @GetMapping
    public ResponseEntity<List<MigrationResponse>> listMigrations(
            @RequestParam UUID apiId) {
        return ResponseEntity.ok(migrationService.listMigrations(apiId));
    }

    @PostMapping("/{id}/rollback")
    @RequiresPermission("environment:rollback")
    public ResponseEntity<MigrationResponse> rollback(@PathVariable UUID id) {
        return ResponseEntity.ok(migrationService.rollback(id));
    }
}
