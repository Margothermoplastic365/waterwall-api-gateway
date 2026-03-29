package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.entity.DeveloperSandboxEntity;
import com.gateway.management.repository.DeveloperSandboxRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Manages developer sandboxes — isolated environments for testing APIs
 * without rate limits, with separate data, and auto-expiration after 30 days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxService {

    private static final int SANDBOX_EXPIRY_DAYS = 30;

    private final DeveloperSandboxRepository sandboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new sandbox for the given user.
     * Each user can have at most one sandbox (enforced by UNIQUE constraint).
     */
    @Transactional
    public DeveloperSandboxEntity createSandbox(UUID userId) {
        sandboxRepository.findByUserId(userId).ifPresent(existing -> {
            throw new IllegalStateException("Sandbox already exists for user: " + userId
                    + ". Delete or reset the existing sandbox first.");
        });

        String defaultConfig;
        String noRateLimits;
        try {
            defaultConfig = objectMapper.writeValueAsString(Map.of(
                    "isolated", true,
                    "dataPrefix", "sandbox_" + userId.toString().substring(0, 8),
                    "logLevel", "DEBUG",
                    "mockUpstreams", true
            ));
            noRateLimits = objectMapper.writeValueAsString(Map.of(
                    "requestsPerSecond", -1,
                    "requestsPerMinute", -1,
                    "requestsPerDay", -1,
                    "burstAllowance", -1,
                    "note", "No rate limits in sandbox mode"
            ));
        } catch (Exception e) {
            defaultConfig = "{}";
            noRateLimits = "{}";
        }

        DeveloperSandboxEntity sandbox = DeveloperSandboxEntity.builder()
                .userId(userId)
                .environmentConfig(defaultConfig)
                .rateLimitOverride(noRateLimits)
                .expiresAt(Instant.now().plus(SANDBOX_EXPIRY_DAYS, ChronoUnit.DAYS))
                .build();

        DeveloperSandboxEntity saved = sandboxRepository.save(sandbox);
        log.info("Created sandbox for user={}, expiresAt={}", userId, saved.getExpiresAt());
        return saved;
    }

    /**
     * Get the sandbox for the given user.
     */
    @Transactional(readOnly = true)
    public DeveloperSandboxEntity getSandbox(UUID userId) {
        return sandboxRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No sandbox found for user: " + userId));
    }

    /**
     * Reset the sandbox: clear data, reset config to defaults, extend expiration.
     */
    @Transactional
    public DeveloperSandboxEntity resetSandbox(UUID userId) {
        DeveloperSandboxEntity sandbox = sandboxRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No sandbox found for user: " + userId));

        String defaultConfig;
        String noRateLimits;
        try {
            defaultConfig = objectMapper.writeValueAsString(Map.of(
                    "isolated", true,
                    "dataPrefix", "sandbox_" + userId.toString().substring(0, 8),
                    "logLevel", "DEBUG",
                    "mockUpstreams", true,
                    "resetAt", Instant.now().toString()
            ));
            noRateLimits = objectMapper.writeValueAsString(Map.of(
                    "requestsPerSecond", -1,
                    "requestsPerMinute", -1,
                    "requestsPerDay", -1,
                    "burstAllowance", -1,
                    "note", "No rate limits in sandbox mode"
            ));
        } catch (Exception e) {
            defaultConfig = "{}";
            noRateLimits = "{}";
        }

        sandbox.setEnvironmentConfig(defaultConfig);
        sandbox.setRateLimitOverride(noRateLimits);
        sandbox.setExpiresAt(Instant.now().plus(SANDBOX_EXPIRY_DAYS, ChronoUnit.DAYS));

        DeveloperSandboxEntity saved = sandboxRepository.save(sandbox);
        log.info("Reset sandbox for user={}, new expiresAt={}", userId, saved.getExpiresAt());
        return saved;
    }

    /**
     * Delete the sandbox for the given user.
     */
    @Transactional
    public void deleteSandbox(UUID userId) {
        sandboxRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No sandbox found for user: " + userId));
        sandboxRepository.deleteByUserId(userId);
        log.info("Deleted sandbox for user={}", userId);
    }
}
