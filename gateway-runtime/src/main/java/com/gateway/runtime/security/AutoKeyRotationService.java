package com.gateway.runtime.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors API keys nearing expiry and auto-rotates them.
 * During a grace period, both old and new keys remain valid
 * for zero-downtime rotation.
 */
@Slf4j
@Service
public class AutoKeyRotationService {

    private static final long GRACE_PERIOD_HOURS = 24;
    private static final long EXPIRY_WARNING_DAYS = 7;

    /** Simulated key store: keyId -> KeyInfo */
    private final ConcurrentHashMap<String, KeyInfo> keyStore = new ConcurrentHashMap<>();

    /** Track rotations */
    private final List<RotationEvent> rotationHistory = new ArrayList<>();

    /**
     * Register a key for rotation tracking.
     */
    public void registerKey(String keyId, String consumerId, Instant expiresAt) {
        keyStore.put(keyId, new KeyInfo(keyId, consumerId, expiresAt, false, null));
        log.info("Registered key for rotation tracking: keyId={}, consumer={}, expiresAt={}",
                keyId, consumerId, expiresAt);
    }

    /**
     * Check if a key is valid (either active or in grace period).
     */
    public boolean isKeyValid(String keyId) {
        KeyInfo info = keyStore.get(keyId);
        if (info == null) {
            return false;
        }
        if (info.inGracePeriod) {
            return Instant.now().isBefore(info.gracePeriodEnd);
        }
        return Instant.now().isBefore(info.expiresAt);
    }

    /**
     * Scheduled check every 5 minutes: find keys nearing expiry and rotate.
     */
    @Scheduled(fixedRate = 300_000)
    public void checkAndRotateKeys() {
        Instant warningThreshold = Instant.now().plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);

        keyStore.values().forEach(keyInfo -> {
            if (keyInfo.inGracePeriod) {
                // Already being rotated
                if (Instant.now().isAfter(keyInfo.gracePeriodEnd)) {
                    keyStore.remove(keyInfo.keyId);
                    log.info("Grace period expired, old key removed: keyId={}", keyInfo.keyId);
                }
                return;
            }

            if (keyInfo.expiresAt.isBefore(warningThreshold)) {
                rotateKey(keyInfo);
            }
        });
    }

    private void rotateKey(KeyInfo oldKey) {
        // Generate new key
        String newKeyId = "key-" + UUID.randomUUID().toString().substring(0, 12);
        Instant newExpiry = Instant.now().plus(90, ChronoUnit.DAYS);

        // Register new key
        keyStore.put(newKeyId, new KeyInfo(newKeyId, oldKey.consumerId, newExpiry, false, null));

        // Set old key to grace period
        Instant graceEnd = Instant.now().plus(GRACE_PERIOD_HOURS, ChronoUnit.HOURS);
        keyStore.put(oldKey.keyId, new KeyInfo(oldKey.keyId, oldKey.consumerId,
                oldKey.expiresAt, true, graceEnd));

        RotationEvent event = new RotationEvent(
                oldKey.keyId, newKeyId, oldKey.consumerId, Instant.now(), graceEnd);
        rotationHistory.add(event);

        log.info("Auto-rotated key: consumer={}, oldKey={}, newKey={}, graceUntil={}",
                oldKey.consumerId, oldKey.keyId, newKeyId, graceEnd);
    }

    public List<RotationEvent> getRotationHistory() {
        return List.copyOf(rotationHistory);
    }

    public int getTrackedKeyCount() {
        return keyStore.size();
    }

    public record RotationEvent(
            String oldKeyId,
            String newKeyId,
            String consumerId,
            Instant rotatedAt,
            Instant graceEndsAt
    ) {}

    private static class KeyInfo {
        final String keyId;
        final String consumerId;
        final Instant expiresAt;
        final boolean inGracePeriod;
        final Instant gracePeriodEnd;

        KeyInfo(String keyId, String consumerId, Instant expiresAt,
                boolean inGracePeriod, Instant gracePeriodEnd) {
            this.keyId = keyId;
            this.consumerId = consumerId;
            this.expiresAt = expiresAt;
            this.inGracePeriod = inGracePeriod;
            this.gracePeriodEnd = gracePeriodEnd;
        }
    }
}
