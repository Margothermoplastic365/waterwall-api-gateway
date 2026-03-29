package com.gateway.identity.service;

import com.gateway.common.cache.CacheInvalidationPublisher;
import com.gateway.common.cache.CacheNames;
import com.gateway.identity.dto.ApiKeyCreatedResponse;
import com.gateway.identity.dto.ApiKeyResponse;
import com.gateway.identity.dto.CreateApiKeyRequest;
import com.gateway.identity.dto.RotateApiKeyResponse;
import com.gateway.identity.dto.ValidateKeyResponse;
import com.gateway.identity.entity.ApiKeyEntity;
import com.gateway.identity.entity.ApplicationEntity;
import com.gateway.identity.entity.RevocationListEntity;
import com.gateway.identity.entity.enums.ApiKeyStatus;
import com.gateway.identity.repository.ApiKeyRepository;
import com.gateway.identity.repository.ApplicationRepository;
import com.gateway.identity.repository.RevocationListRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for API key lifecycle management.
 * <p>
 * Key format: {@code gw_{env}_{32-hex-random}} (e.g. {@code gw_live_a1b2c3d4...}).
 * Only the SHA-256 hash of the full key is persisted; the plaintext key is returned
 * exactly once at creation time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApplicationRepository applicationRepository;
    private final RevocationListRepository revocationListRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final AuditService auditService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int KEY_PREFIX_LENGTH = 12;
    private static final int REVOCATION_EXPIRY_HOURS = 24;

    @Value("${gateway.apikey.prefix:live}")
    private String envPrefix;

    @Value("${gateway.apikey.rotation-grace-hours:168}")
    private long rotationGraceHours;

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Generate a new API key for an application.
     * The full plaintext key is returned only once in the response.
     *
     * @param appId   the application ID
     * @param request the creation payload (optional key name)
     * @return the created key with the full plaintext key
     * @throws EntityNotFoundException if the application does not exist
     */
    @Transactional
    public ApiKeyCreatedResponse generateApiKey(UUID appId, CreateApiKeyRequest request) {
        ApplicationEntity app = findApplicationOrThrow(appId);

        String envSlug = (request != null && request.getEnvironmentSlug() != null)
                ? request.getEnvironmentSlug() : "dev";
        // Environment-aware prefix: dev_, uat_, stg_, live_
        String envPrefix = switch (envSlug) {
            case "prod" -> "live_";
            case "staging" -> "stg_";
            case "uat" -> "uat_";
            default -> "dev_";
        };

        String fullKey = envPrefix + generateRawKey();
        String keyHash = sha256Hex(fullKey);
        String keyPrefix = fullKey.substring(0, envPrefix.length() + KEY_PREFIX_LENGTH);

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .application(app)
                .name(request != null ? request.getName() : null)
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .environmentSlug(envSlug)
                .status(ApiKeyStatus.ACTIVE)
                .build();

        ApiKeyEntity saved = apiKeyRepository.save(entity);
        log.info("API key created: id={}, prefix={}, appId={}", saved.getId(), keyPrefix, appId);

        auditService.logEvent("apikey.created", "API_KEY", saved.getId().toString(), "SUCCESS");

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .keyPrefix(keyPrefix)
                .fullKey(fullKey)
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * List all API keys for an application. Only the key prefix is exposed.
     *
     * @param appId the application ID
     * @return list of key summaries (prefix only, never hashes)
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID appId) {
        return apiKeyRepository.findByApplicationId(appId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Revoke an API key. The key is marked as REVOKED, added to the
     * revocation list for distributed cache invalidation, and a cache
     * eviction message is published.
     *
     * @param appId the application ID
     * @param keyId the API key ID
     * @throws EntityNotFoundException  if the key does not exist
     * @throws IllegalArgumentException if the key does not belong to the application
     */
    @Transactional
    public void revokeApiKey(UUID appId, UUID keyId) {
        ApiKeyEntity key = findKeyForApp(appId, keyId);

        key.setStatus(ApiKeyStatus.REVOKED);
        apiKeyRepository.save(key);

        addToRevocationList(key, "Explicitly revoked");
        publishCacheInvalidation(key.getKeyHash(), "API key revoked: " + key.getKeyPrefix());

        log.info("API key revoked: id={}, prefix={}, appId={}", keyId, key.getKeyPrefix(), appId);
        auditService.logEvent("apikey.revoked", "API_KEY", keyId.toString(), "SUCCESS");
    }

    /**
     * Rotate an API key: generate a new key and set the old key to ROTATED status
     * with a configurable grace period before expiry.
     *
     * @param appId the application ID
     * @param keyId the old API key ID
     * @return both old and new key information
     * @throws EntityNotFoundException  if the key does not exist
     * @throws IllegalArgumentException if the key does not belong to the application
     */
    @Transactional
    public RotateApiKeyResponse rotateApiKey(UUID appId, UUID keyId) {
        ApiKeyEntity oldKey = findKeyForApp(appId, keyId);
        ApplicationEntity app = oldKey.getApplication();

        // Generate a new key
        String newFullKey = generateRawKey();
        String newKeyHash = sha256Hex(newFullKey);
        String newKeyPrefix = newFullKey.substring(0, KEY_PREFIX_LENGTH);

        ApiKeyEntity newKey = ApiKeyEntity.builder()
                .application(app)
                .name(oldKey.getName())
                .keyPrefix(newKeyPrefix)
                .keyHash(newKeyHash)
                .status(ApiKeyStatus.ACTIVE)
                .build();

        ApiKeyEntity savedNewKey = apiKeyRepository.save(newKey);

        // Retire old key with grace period
        Instant graceExpiry = Instant.now().plus(rotationGraceHours, ChronoUnit.HOURS);
        oldKey.setStatus(ApiKeyStatus.ROTATED);
        oldKey.setExpiresAt(graceExpiry);
        apiKeyRepository.save(oldKey);

        publishCacheInvalidation(oldKey.getKeyHash(), "API key rotated: " + oldKey.getKeyPrefix());

        log.info("API key rotated: oldId={}, newId={}, appId={}, graceUntil={}",
                keyId, savedNewKey.getId(), appId, graceExpiry);
        auditService.logEvent("apikey.rotated", "API_KEY", keyId.toString(), "SUCCESS");

        return RotateApiKeyResponse.builder()
                .newKeyId(savedNewKey.getId())
                .newKeyPrefix(newKeyPrefix)
                .newFullKey(newFullKey)
                .oldKeyId(oldKey.getId())
                .oldKeyPrefix(oldKey.getKeyPrefix())
                .oldKeyExpiresAt(graceExpiry)
                .build();
    }

    /**
     * Validate an API key by its SHA-256 hash. Called by the gateway runtime
     * to authenticate incoming requests.
     *
     * @param keyHash the SHA-256 hex hash of the full API key
     * @return application and user context if the key is valid, or null/status info otherwise
     */
    @Transactional(readOnly = true)
    public ValidateKeyResponse validateKey(String keyHash) {
        ApiKeyEntity key = apiKeyRepository.findByKeyHash(keyHash)
                .orElse(null);

        if (key == null) {
            return ValidateKeyResponse.builder()
                    .status("INVALID")
                    .build();
        }

        // Check status
        if (key.getStatus() != ApiKeyStatus.ACTIVE && key.getStatus() != ApiKeyStatus.ROTATED) {
            return ValidateKeyResponse.builder()
                    .status(key.getStatus().name())
                    .build();
        }

        // Check expiry (relevant for ROTATED keys in their grace period)
        if (key.getExpiresAt() != null && Instant.now().isAfter(key.getExpiresAt())) {
            return ValidateKeyResponse.builder()
                    .status("EXPIRED")
                    .build();
        }

        ApplicationEntity app = key.getApplication();
        return ValidateKeyResponse.builder()
                .applicationId(app.getId())
                .applicationName(app.getName())
                .userId(app.getUser().getId())
                .orgId(app.getOrganization() != null ? app.getOrganization().getId() : null)
                .status(key.getStatus().name())
                .environmentSlug(key.getEnvironmentSlug())
                .build();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private ApplicationEntity findApplicationOrThrow(UUID appId) {
        return applicationRepository.findById(appId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + appId));
    }

    private ApiKeyEntity findKeyForApp(UUID appId, UUID keyId) {
        ApiKeyEntity key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new EntityNotFoundException("API key not found: " + keyId));

        if (!key.getApplication().getId().equals(appId)) {
            throw new IllegalArgumentException(
                    "API key " + keyId + " does not belong to application " + appId);
        }
        return key;
    }

    /**
     * Generate a raw API key string: {@code gw_{env}_{32-hex-random-chars}}.
     */
    private String generateRawKey() {
        byte[] randomBytes = new byte[16]; // 16 bytes = 32 hex chars
        SECURE_RANDOM.nextBytes(randomBytes);
        String hexRandom = bytesToHex(randomBytes);
        return "gw_" + envPrefix + "_" + hexRandom;
    }

    /**
     * Compute SHA-256 hash and return as a lowercase hex string.
     */
    static String sha256Hex(String input) {
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

    private void addToRevocationList(ApiKeyEntity key, String reason) {
        RevocationListEntity entry = RevocationListEntity.builder()
                .revocationType("API_KEY")
                .credentialId(key.getKeyPrefix())
                .reason(reason)
                .revokedAt(Instant.now())
                .expiresAt(Instant.now().plus(REVOCATION_EXPIRY_HOURS, ChronoUnit.HOURS))
                .build();
        revocationListRepository.save(entry);
    }

    private void publishCacheInvalidation(String keyHash, String reason) {
        try {
            cacheInvalidationPublisher.invalidate(CacheNames.API_KEYS, keyHash, reason);
        } catch (Exception ex) {
            // Cache invalidation failure should not break the main flow
            log.error("Failed to publish cache invalidation for key hash: {}", keyHash, ex);
        }
    }

    private ApiKeyResponse toResponse(ApiKeyEntity entity) {
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .keyPrefix(entity.getKeyPrefix())
                .environmentSlug(entity.getEnvironmentSlug())
                .status(entity.getStatus())
                .lastUsedAt(entity.getLastUsedAt())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
