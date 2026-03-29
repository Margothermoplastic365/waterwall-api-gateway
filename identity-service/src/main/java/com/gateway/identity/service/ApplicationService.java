package com.gateway.identity.service;

import com.gateway.identity.dto.ApplicationResponse;
import com.gateway.identity.dto.BasicAuthSecretResponse;
import com.gateway.identity.dto.CreateApplicationRequest;
import com.gateway.identity.dto.UpdateApplicationRequest;
import com.gateway.identity.entity.ApplicationEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.ApiKeyStatus;
import com.gateway.identity.repository.ApiKeyRepository;
import com.gateway.identity.repository.ApplicationRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing developer applications.
 * Each application can have multiple API keys and belongs to a single user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Create a new application for the given user.
     *
     * @param userId  the owning user's ID
     * @param request the creation payload
     * @return the created application
     */
    @Transactional
    public ApplicationResponse createApplication(UUID userId, CreateApplicationRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        ApplicationEntity app = ApplicationEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .callbackUrls(request.getCallbackUrls())
                .user(user)
                .status("ACTIVE")
                .build();

        ApplicationEntity saved = applicationRepository.save(app);
        log.info("Application created: id={}, name={}, userId={}", saved.getId(), saved.getName(), userId);

        auditService.logEvent("application.created", "APPLICATION", saved.getId().toString(), "SUCCESS");

        return toResponse(saved);
    }

    /**
     * List all applications owned by the given user.
     *
     * @param userId the owner's ID
     * @return list of application summaries
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> listMyApplications(UUID userId) {
        return applicationRepository.findByUserIdAndStatusNot(userId, "DELETED").stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieve a single application by ID.
     *
     * @param appId the application ID
     * @return the application details
     * @throws EntityNotFoundException if the application does not exist
     */
    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(UUID appId) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        return toResponse(app);
    }

    /**
     * Update an existing application. Only non-null fields in the request are applied.
     *
     * @param appId   the application ID
     * @param userId  the requesting user's ID (used for ownership verification)
     * @param request the update payload with optional fields
     * @return the updated application
     * @throws EntityNotFoundException  if the application does not exist
     * @throws IllegalArgumentException if the user does not own the application
     */
    @Transactional
    public ApplicationResponse updateApplication(UUID appId, UUID userId, UpdateApplicationRequest request) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        verifyOwnership(app, userId);

        if (request.getName() != null) {
            app.setName(request.getName());
        }
        if (request.getDescription() != null) {
            app.setDescription(request.getDescription());
        }
        if (request.getCallbackUrls() != null) {
            app.setCallbackUrls(request.getCallbackUrls());
        }

        ApplicationEntity saved = applicationRepository.save(app);
        log.info("Application updated: id={}, userId={}", appId, userId);

        auditService.logEvent("application.updated", "APPLICATION", appId.toString(), "SUCCESS");

        return toResponse(saved);
    }

    /**
     * Soft-delete an application by setting its status to DELETED.
     * All API keys belonging to this application are revoked.
     *
     * @param appId  the application ID
     * @param userId the requesting user's ID (used for ownership verification)
     * @throws EntityNotFoundException  if the application does not exist
     * @throws IllegalArgumentException if the user does not own the application
     */
    @Transactional
    public void deleteApplication(UUID appId, UUID userId) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        verifyOwnership(app, userId);

        // Revoke all API keys for this application
        apiKeyRepository.findByApplicationId(appId).forEach(key -> {
            if (key.getStatus() == ApiKeyStatus.ACTIVE || key.getStatus() == ApiKeyStatus.ROTATED) {
                key.setStatus(ApiKeyStatus.REVOKED);
                apiKeyRepository.save(key);
            }
        });

        // Soft delete the application
        app.setStatus("DELETED");
        applicationRepository.save(app);

        log.info("Application deleted (soft): id={}, userId={}", appId, userId);
        auditService.logEvent("application.deleted", "APPLICATION", appId.toString(), "SUCCESS");
    }

    /**
     * Generate (or regenerate) a Basic Auth secret for an application.
     * The plaintext secret is returned only once; only the SHA-256 hash is stored.
     *
     * @param appId  the application ID
     * @param userId the requesting user's ID (ownership check)
     * @return the client ID and plaintext secret (shown once)
     */
    @Transactional
    public BasicAuthSecretResponse generateBasicAuthSecret(UUID appId, UUID userId) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        verifyOwnership(app, userId);

        String secret = generateRandomSecret();
        String hash = sha256Hex(secret);

        app.setBasicAuthSecretHash(hash);
        applicationRepository.save(app);

        log.info("Basic auth secret generated for application: id={}, userId={}", appId, userId);
        auditService.logEvent("application.basic_auth_generated", "APPLICATION", appId.toString(), "SUCCESS");

        return BasicAuthSecretResponse.builder()
                .clientId(appId.toString())
                .clientSecret(secret)
                .build();
    }

    /**
     * Revoke (remove) Basic Auth credentials for an application.
     *
     * @param appId  the application ID
     * @param userId the requesting user's ID (ownership check)
     */
    @Transactional
    public void revokeBasicAuth(UUID appId, UUID userId) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        verifyOwnership(app, userId);

        app.setBasicAuthSecretHash(null);
        applicationRepository.save(app);

        log.info("Basic auth revoked for application: id={}, userId={}", appId, userId);
        auditService.logEvent("application.basic_auth_revoked", "APPLICATION", appId.toString(), "SUCCESS");
    }

    /**
     * Check whether an application has Basic Auth credentials configured.
     *
     * @param appId the application ID
     * @return true if basic auth is configured
     */
    @Transactional(readOnly = true)
    public boolean hasBasicAuth(UUID appId) {
        ApplicationEntity app = findApplicationOrThrow(appId);
        return app.getBasicAuthSecretHash() != null;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateRandomSecret() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private ApplicationEntity findApplicationOrThrow(UUID appId) {
        return applicationRepository.findById(appId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + appId));
    }

    private void verifyOwnership(ApplicationEntity app, UUID userId) {
        if (!app.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User " + userId + " does not own application " + app.getId());
        }
    }

    private ApplicationResponse toResponse(ApplicationEntity app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .name(app.getName())
                .description(app.getDescription())
                .callbackUrls(app.getCallbackUrls())
                .status(app.getStatus())
                .orgId(app.getOrganization() != null ? app.getOrganization().getId() : null)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
