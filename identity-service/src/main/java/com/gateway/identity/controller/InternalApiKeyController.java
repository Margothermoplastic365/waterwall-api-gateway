package com.gateway.identity.controller;

import com.gateway.identity.dto.ValidateCertResponse;
import com.gateway.identity.dto.ValidateKeyResponse;
import com.gateway.identity.service.ApiKeyService;
import com.gateway.identity.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST controller for API key validation used by the gateway-runtime.
 * <p>
 * This endpoint is intended for service-to-service communication on the
 * internal network and does <b>not</b> require JWT authentication.
 * The path {@code /v1/internal/**} should be permitted without auth in
 * {@code SecurityConfig}.
 * <p>
 * All error responses are handled by the {@code GlobalExceptionHandler} and
 * returned as {@code ApiErrorResponse}.
 */
@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
public class InternalApiKeyController {

    private final ApiKeyService apiKeyService;
    private final CertificateService certificateService;

    /**
     * Validate an API key by its hash.
     * Called by gateway-runtime during request authentication.
     *
     * @param keyHash the SHA-256 hash of the API key
     * @return 200 OK with key/application metadata if valid, or 404 if not found
     */
    @GetMapping("/validate-key")
    public ResponseEntity<ValidateKeyResponse> validateKey(@RequestParam("keyHash") String keyHash) {
        ValidateKeyResponse response = apiKeyService.validateKey(keyHash);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate a client certificate by CN and fingerprint.
     * Called by gateway-runtime during mTLS authentication.
     *
     * @param cn          the Subject CN from the client certificate
     * @param fingerprint the SHA-256 fingerprint of the certificate
     * @return 200 OK with application metadata if valid
     */
    @GetMapping("/validate-cert")
    public ResponseEntity<ValidateCertResponse> validateCert(
            @RequestParam("cn") String cn,
            @RequestParam("fingerprint") String fingerprint) {
        ValidateCertResponse response = certificateService.validateCert(cn, fingerprint);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate basic auth credentials (application client ID + secret hash).
     * Called by gateway-runtime during Basic Auth authentication.
     *
     * @param clientId   the application ID used as the basic auth username
     * @param secretHash the SHA-256 hash of the basic auth password
     * @return 200 OK with application metadata if valid
     */
    @GetMapping("/validate-basic")
    public ResponseEntity<ValidateCertResponse> validateBasicAuth(
            @RequestParam("clientId") String clientId,
            @RequestParam("secretHash") String secretHash) {
        ValidateCertResponse response = certificateService.validateBasicAuth(clientId, secretHash);
        return ResponseEntity.ok(response);
    }
}
