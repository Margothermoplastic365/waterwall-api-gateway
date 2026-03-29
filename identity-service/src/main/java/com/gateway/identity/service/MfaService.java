package com.gateway.identity.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.identity.dto.MfaSetupResponse;
import com.gateway.identity.dto.MfaStatusResponse;
import com.gateway.identity.entity.MfaSecretEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.MfaType;
import com.gateway.identity.repository.MfaSecretRepository;
import com.gateway.identity.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service handling all MFA (Multi-Factor Authentication) operations:
 * TOTP (Google Authenticator), Email OTP, and recovery codes.
 */
@Slf4j
@Service
public class MfaService {

    private static final int TOTP_SECRET_BYTES = 20;
    private static final int TOTP_CODE_DIGITS = 6;
    private static final Duration TOTP_TIME_STEP = Duration.ofSeconds(30);
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;
    private static final int EMAIL_OTP_DIGITS = 6;
    private static final Duration EMAIL_OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_TOTP_ATTEMPTS_PER_MINUTE = 5;
    private static final Duration EMAIL_OTP_RATE_LIMIT = Duration.ofSeconds(60);

    private static final String ISSUER = "APIGateway";

    // Base32 alphabet (RFC 4648)
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final MfaSecretRepository mfaSecretRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EventPublisher eventPublisher;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TimeBasedOneTimePasswordGenerator totpGenerator;

    /** Rate-limit cache: userId -> attempt counter (expires after 1 minute). */
    private final Cache<UUID, AtomicInteger> totpAttemptCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    /** Rate-limit cache for email OTP sends: userId -> last send timestamp. */
    private final Cache<UUID, Instant> emailOtpRateCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    public MfaService(MfaSecretRepository mfaSecretRepository,
                      UserRepository userRepository,
                      AuditService auditService,
                      EventPublisher eventPublisher) {
        this.mfaSecretRepository = mfaSecretRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;

        this.totpGenerator = new TimeBasedOneTimePasswordGenerator(TOTP_TIME_STEP, TOTP_CODE_DIGITS);
    }

    // ── TOTP Setup ──────────────────────────────────────────────────────

    /**
     * Initiate TOTP setup for a user. Generates a secret, QR code, and recovery codes.
     * The TOTP factor remains disabled until {@link #verifyAndEnableTotp} is called.
     */
    @Transactional
    public MfaSetupResponse setupTotp(UUID userId) {
        UserEntity user = findUserOrThrow(userId);

        // Generate 20-byte secret and encode as Base32
        byte[] secretBytes = new byte[TOTP_SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        String base32Secret = encodeBase32(secretBytes);

        // Generate QR code data URL
        String otpAuthUri = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                ISSUER, user.getEmail(), base32Secret, ISSUER);
        String qrCodeDataUrl = generateQrCodeDataUrl(otpAuthUri);

        // Generate recovery codes
        List<String> recoveryCodes = generateRecoveryCodes();

        // Persist (upsert — replace any existing non-enabled TOTP setup)
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP)
                .orElse(MfaSecretEntity.builder()
                        .user(user)
                        .type(MfaType.TOTP)
                        .build());
        entity.setSecretEncrypted(base32Secret);
        entity.setEnabled(false);
        entity.setRecoveryCodes(recoveryCodes);
        mfaSecretRepository.save(entity);

        auditService.logEvent("mfa.totp.setup.initiated", "USER",
                userId.toString(), "SUCCESS",
                user.getEmail(), userId);

        log.info("TOTP setup initiated for user: id={}", userId);

        return MfaSetupResponse.builder()
                .secretKey(base32Secret)
                .qrCodeDataUrl(qrCodeDataUrl)
                .recoveryCodes(recoveryCodes)
                .build();
    }

    // ── TOTP Verify & Enable ────────────────────────────────────────────

    /**
     * Verify a TOTP code and enable the TOTP factor for the user.
     * Called once during initial setup to confirm the authenticator is configured correctly.
     */
    @Transactional
    public void verifyAndEnableTotp(UUID userId, String code) {
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP)
                .orElseThrow(() -> new IllegalStateException("TOTP has not been set up for this user"));

        if (!validateTotpCode(entity.getSecretEncrypted(), code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        entity.setEnabled(true);
        mfaSecretRepository.save(entity);

        UserEntity user = entity.getUser();
        auditService.logEvent("mfa.totp.enabled", "USER",
                userId.toString(), "SUCCESS",
                user.getEmail(), userId);

        log.info("TOTP enabled for user: id={}", userId);
    }

    // ── TOTP Verify (login flow) ────────────────────────────────────────

    /**
     * Verify a TOTP code or recovery code during login.
     * Rate-limited to {@value #MAX_TOTP_ATTEMPTS_PER_MINUTE} attempts per minute per user.
     */
    @Transactional
    public boolean verifyTotp(UUID userId, String code) {
        // Rate limit check
        AtomicInteger attempts = totpAttemptCache.get(userId, k -> new AtomicInteger(0));
        if (attempts.incrementAndGet() > MAX_TOTP_ATTEMPTS_PER_MINUTE) {
            log.warn("TOTP rate limit exceeded for user: id={}", userId);
            throw new IllegalStateException("Too many verification attempts. Please try again later.");
        }

        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP)
                .filter(MfaSecretEntity::getEnabled)
                .orElseThrow(() -> new IllegalStateException("TOTP is not enabled for this user"));

        // Try TOTP code first
        if (validateTotpCode(entity.getSecretEncrypted(), code)) {
            return true;
        }

        // Try recovery code
        if (entity.getRecoveryCodes() != null && entity.getRecoveryCodes().contains(code)) {
            List<String> updatedCodes = new ArrayList<>(entity.getRecoveryCodes());
            updatedCodes.remove(code);
            entity.setRecoveryCodes(updatedCodes);
            mfaSecretRepository.save(entity);
            log.info("Recovery code used for user: id={}, remaining={}", userId, updatedCodes.size());
            return true;
        }

        return false;
    }

    // ── TOTP Disable ────────────────────────────────────────────────────

    /**
     * Disable TOTP for a user. Requires a valid current TOTP code as proof of possession.
     */
    @Transactional
    public void disableTotp(UUID userId, String code) {
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP)
                .filter(MfaSecretEntity::getEnabled)
                .orElseThrow(() -> new IllegalStateException("TOTP is not enabled for this user"));

        if (!validateTotpCode(entity.getSecretEncrypted(), code)) {
            throw new IllegalArgumentException("Invalid TOTP code — cannot disable MFA");
        }

        mfaSecretRepository.delete(entity);

        UserEntity user = entity.getUser();
        auditService.logEvent("mfa.totp.disabled", "USER",
                userId.toString(), "SUCCESS",
                user.getEmail(), userId);

        log.info("TOTP disabled for user: id={}", userId);
    }

    // ── Recovery Code Regeneration ──────────────────────────────────────

    /**
     * Regenerate recovery codes for a user's TOTP factor. Old codes are replaced.
     * Returns the new codes (shown once to the user).
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId) {
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP)
                .filter(MfaSecretEntity::getEnabled)
                .orElseThrow(() -> new IllegalStateException("TOTP is not enabled for this user"));

        List<String> newCodes = generateRecoveryCodes();
        entity.setRecoveryCodes(newCodes);
        mfaSecretRepository.save(entity);

        UserEntity user = entity.getUser();
        auditService.logEvent("mfa.recovery.regenerated", "USER",
                userId.toString(), "SUCCESS",
                user.getEmail(), userId);

        log.info("Recovery codes regenerated for user: id={}", userId);
        return newCodes;
    }

    // ── Email OTP ───────────────────────────────────────────────────────

    /**
     * Send a one-time email OTP to the user's registered email address.
     * Rate-limited to 1 per 60 seconds per user.
     */
    @Transactional
    public void sendEmailOtp(UUID userId) {
        // Rate limit
        Instant lastSent = emailOtpRateCache.getIfPresent(userId);
        if (lastSent != null && lastSent.plus(EMAIL_OTP_RATE_LIMIT).isAfter(Instant.now())) {
            throw new IllegalStateException("Email OTP already sent recently. Please wait before requesting another.");
        }

        UserEntity user = findUserOrThrow(userId);

        // Generate 6-digit code
        String otpCode = generateNumericCode(EMAIL_OTP_DIGITS);

        // Hash with SHA-256 for storage
        String otpHash = sha256(otpCode);

        // Upsert EMAIL MFA secret
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.EMAIL)
                .orElse(MfaSecretEntity.builder()
                        .user(user)
                        .type(MfaType.EMAIL)
                        .enabled(true)
                        .build());
        entity.setSecretEncrypted(otpHash);
        entity.setRecoveryCodes(null);
        mfaSecretRepository.save(entity);

        emailOtpRateCache.put(userId, Instant.now());

        // Publish notification event to RabbitMQ
        publishEmailOtpEvent(user.getEmail(), otpCode, userId);

        auditService.logEvent("mfa.email.otp.sent", "USER",
                userId.toString(), "SUCCESS",
                user.getEmail(), userId);

        log.info("Email OTP sent for user: id={}", userId);
    }

    /**
     * Verify a 6-digit email OTP code. Single-use: the OTP record is deleted on success.
     * Expires after 5 minutes from creation.
     */
    @Transactional
    public boolean verifyEmailOtp(UUID userId, String code) {
        MfaSecretEntity entity = mfaSecretRepository.findByUserIdAndType(userId, MfaType.EMAIL)
                .orElse(null);

        if (entity == null) {
            return false;
        }

        // Check TTL (5 minutes from created_at)
        if (entity.getCreatedAt() != null
                && entity.getCreatedAt().plus(EMAIL_OTP_TTL).isBefore(Instant.now())) {
            mfaSecretRepository.delete(entity);
            return false;
        }

        // Compare SHA-256 hash
        String codeHash = sha256(code);
        if (!MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8),
                entity.getSecretEncrypted().getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        // Valid — delete the single-use OTP record
        mfaSecretRepository.delete(entity);
        return true;
    }

    // ── MFA Status ──────────────────────────────────────────────────────

    /**
     * Check which MFA factors are enabled for a user and count remaining recovery codes.
     */
    public MfaStatusResponse getMfaStatus(UUID userId) {
        boolean totpEnabled = false;
        boolean emailOtpEnabled = false;
        boolean smsOtpEnabled = false;
        int recoveryCodesRemaining = 0;

        Optional<MfaSecretEntity> totpOpt = mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP);
        if (totpOpt.isPresent() && Boolean.TRUE.equals(totpOpt.get().getEnabled())) {
            totpEnabled = true;
            List<String> codes = totpOpt.get().getRecoveryCodes();
            recoveryCodesRemaining = codes != null ? codes.size() : 0;
        }

        Optional<MfaSecretEntity> emailOpt = mfaSecretRepository.findByUserIdAndType(userId, MfaType.EMAIL);
        if (emailOpt.isPresent() && Boolean.TRUE.equals(emailOpt.get().getEnabled())) {
            emailOtpEnabled = true;
        }

        Optional<MfaSecretEntity> smsOpt = mfaSecretRepository.findByUserIdAndType(userId, MfaType.SMS);
        if (smsOpt.isPresent() && Boolean.TRUE.equals(smsOpt.get().getEnabled())) {
            smsOtpEnabled = true;
        }

        return MfaStatusResponse.builder()
                .totpEnabled(totpEnabled)
                .emailOtpEnabled(emailOtpEnabled)
                .smsOtpEnabled(smsOtpEnabled)
                .recoveryCodesRemaining(recoveryCodesRemaining)
                .build();
    }

    // ── Public helper: check if user has any enabled MFA ────────────────

    /**
     * Returns the list of enabled MFA factor type names for the user.
     * Used by the login flow to determine if MFA is required.
     */
    public List<String> getEnabledFactors(UUID userId) {
        return mfaSecretRepository.findByUserIdAndEnabledTrue(userId).stream()
                .map(e -> e.getType().name())
                .collect(Collectors.toList());
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private UserEntity findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * Validate a TOTP code against the secret with +/-1 time step tolerance.
     */
    private boolean validateTotpCode(String base32Secret, String code) {
        try {
            byte[] keyBytes = decodeBase32(base32Secret);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, totpGenerator.getAlgorithm());

            Instant now = Instant.now();
            // Check current, previous, and next time steps
            for (int i = -1; i <= 1; i++) {
                Instant timeStep = now.plus(TOTP_TIME_STEP.multipliedBy(i));
                int expectedCode = totpGenerator.generateOneTimePassword(keySpec, timeStep);
                String expectedStr = String.format("%0" + TOTP_CODE_DIGITS + "d", expectedCode);
                if (expectedStr.equals(code)) {
                    return true;
                }
            }
        } catch (InvalidKeyException e) {
            log.error("Invalid TOTP key for validation", e);
        }
        return false;
    }

    /**
     * Encode raw bytes to Base32 (RFC 4648, no padding).
     */
    private String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    /**
     * Decode a Base32-encoded string back to raw bytes.
     */
    private byte[] decodeBase32(String encoded) {
        String upper = encoded.toUpperCase().replaceAll("[=]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : upper.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * Generate a QR code PNG as a data:image/png;base64 URL.
     */
    private String generateQrCodeDataUrl(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            String base64 = Base64.getEncoder().encodeToString(pngOut.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate a list of random alphanumeric recovery codes.
     */
    private List<String> generateRecoveryCodes() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /**
     * Generate a random numeric code with the given number of digits.
     */
    private String generateNumericCode(int digits) {
        int bound = (int) Math.pow(10, digits);
        int code = secureRandom.nextInt(bound);
        return String.format("%0" + digits + "d", code);
    }

    /**
     * SHA-256 hash of the input string, returned as a hex string.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Publish an email OTP notification event to RabbitMQ.
     */
    private void publishEmailOtpEvent(String email, String otpCode, UUID userId) {
        try {
            EmailOtpEvent event = EmailOtpEvent.builder()
                    .eventType("notification.email")
                    .actorId(userId.toString())
                    .recipientEmail(email)
                    .otpCode(otpCode)
                    .subject("Your verification code")
                    .build();
            eventPublisher.publish(RabbitMQExchanges.NOTIFICATIONS, "notification.email", event);
        } catch (Exception ex) {
            log.error("Failed to publish email OTP event for user: id={}", userId, ex);
        }
    }

    // ── Inner event class ───────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class EmailOtpEvent extends BaseEvent {
        private String recipientEmail;
        private String otpCode;
        private String subject;
    }
}
