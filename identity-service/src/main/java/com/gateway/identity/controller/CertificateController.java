package com.gateway.identity.controller;

import com.gateway.identity.dto.CertificateResponse;
import com.gateway.identity.dto.UploadCertRequest;
import com.gateway.identity.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing client certificates bound to an application.
 * Used by developers to register certificates for mTLS authentication.
 */
@RestController
@RequestMapping("/v1/applications/{appId}/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * Upload a new client certificate in PEM format.
     *
     * @param appId   the application ID
     * @param request the PEM certificate payload
     * @return the parsed certificate metadata
     */
    @PostMapping
    public ResponseEntity<CertificateResponse> uploadCertificate(
            @PathVariable UUID appId,
            @Valid @RequestBody UploadCertRequest request) {
        CertificateResponse response = certificateService.uploadCertificate(appId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all certificates for an application.
     *
     * @param appId the application ID
     * @return list of certificate summaries
     */
    @GetMapping
    public ResponseEntity<List<CertificateResponse>> listCertificates(@PathVariable UUID appId) {
        return ResponseEntity.ok(certificateService.listCertificates(appId));
    }

    /**
     * Revoke a certificate by ID.
     *
     * @param appId  the application ID
     * @param certId the certificate ID to revoke
     * @return 204 No Content on success
     */
    @DeleteMapping("/{certId}")
    public ResponseEntity<Void> revokeCertificate(
            @PathVariable UUID appId,
            @PathVariable UUID certId) {
        certificateService.revokeCertificate(appId, certId);
        return ResponseEntity.noContent().build();
    }
}
