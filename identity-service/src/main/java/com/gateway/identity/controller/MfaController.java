package com.gateway.identity.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.identity.dto.MfaSetupResponse;
import com.gateway.identity.dto.MfaStatusResponse;
import com.gateway.identity.dto.MfaVerifyRequest;
import com.gateway.identity.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for MFA (Multi-Factor Authentication) operations.
 * All endpoints require an authenticated user.
 */
@RestController
@RequestMapping("/v1/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    // ── TOTP ────────────────────────────────────────────────────────────

    /**
     * Initiate TOTP setup — returns secret, QR code, and recovery codes.
     */
    @PostMapping("/totp/setup")
    public ResponseEntity<MfaSetupResponse> setupTotp() {
        UUID userId = resolveUserId();
        MfaSetupResponse response = mfaService.setupTotp(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify a TOTP code and enable the factor.
     */
    @PostMapping("/totp/verify")
    public ResponseEntity<Void> verifyAndEnableTotp(@Valid @RequestBody MfaVerifyRequest request) {
        UUID userId = resolveUserId();
        mfaService.verifyAndEnableTotp(userId, request.getCode());
        return ResponseEntity.noContent().build();
    }

    /**
     * Disable TOTP (requires a valid current TOTP code as proof).
     */
    @PostMapping("/totp/disable")
    public ResponseEntity<Void> disableTotp(@Valid @RequestBody MfaVerifyRequest request) {
        UUID userId = resolveUserId();
        mfaService.disableTotp(userId, request.getCode());
        return ResponseEntity.noContent().build();
    }

    // ── Recovery Codes ──────────────────────────────────────────────────

    /**
     * Regenerate recovery codes (replaces existing ones).
     */
    @PostMapping("/recovery/regenerate")
    public ResponseEntity<List<String>> regenerateRecoveryCodes() {
        UUID userId = resolveUserId();
        List<String> codes = mfaService.regenerateRecoveryCodes(userId);
        return ResponseEntity.ok(codes);
    }

    // ── Email OTP ───────────────────────────────────────────────────────

    /**
     * Send a one-time OTP code to the user's registered email.
     */
    @PostMapping("/email/send")
    public ResponseEntity<Void> sendEmailOtp() {
        UUID userId = resolveUserId();
        mfaService.sendEmailOtp(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify a 6-digit email OTP code.
     */
    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyEmailOtp(@Valid @RequestBody MfaVerifyRequest request) {
        UUID userId = resolveUserId();
        boolean valid = mfaService.verifyEmailOtp(userId, request.getCode());
        if (!valid) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    // ── Status ──────────────────────────────────────────────────────────

    /**
     * Get the current MFA status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<MfaStatusResponse> getMfaStatus() {
        UUID userId = resolveUserId();
        MfaStatusResponse response = mfaService.getMfaStatus(userId);
        return ResponseEntity.ok(response);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private UUID resolveUserId() {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return UUID.fromString(userId);
    }
}
