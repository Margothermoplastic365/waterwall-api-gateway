package com.gateway.management.controller;

import com.gateway.management.entity.DeveloperSandboxEntity;
import com.gateway.management.service.SandboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Developer sandbox management endpoints.
 * Sandboxes provide isolated environments for testing APIs
 * without rate limits and with auto-expiration.
 */
@RestController
@RequestMapping("/v1/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    /**
     * Create a sandbox for the current user.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSandbox(@RequestParam UUID userId) {
        DeveloperSandboxEntity sandbox = sandboxService.createSandbox(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(sandbox));
    }

    /**
     * Get the current user's sandbox.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSandbox(@RequestParam UUID userId) {
        DeveloperSandboxEntity sandbox = sandboxService.getSandbox(userId);
        return ResponseEntity.ok(toResponse(sandbox));
    }

    /**
     * Reset the current user's sandbox.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSandbox(@RequestParam UUID userId) {
        DeveloperSandboxEntity sandbox = sandboxService.resetSandbox(userId);
        return ResponseEntity.ok(toResponse(sandbox));
    }

    /**
     * Delete the current user's sandbox.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteSandbox(@RequestParam UUID userId) {
        sandboxService.deleteSandbox(userId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toResponse(DeveloperSandboxEntity sandbox) {
        return Map.of(
                "id", sandbox.getId(),
                "userId", sandbox.getUserId(),
                "environmentConfig", sandbox.getEnvironmentConfig() != null ? sandbox.getEnvironmentConfig() : "{}",
                "rateLimitOverride", sandbox.getRateLimitOverride() != null ? sandbox.getRateLimitOverride() : "{}",
                "createdAt", sandbox.getCreatedAt() != null ? sandbox.getCreatedAt().toString() : "",
                "expiresAt", sandbox.getExpiresAt() != null ? sandbox.getExpiresAt().toString() : ""
        );
    }
}
