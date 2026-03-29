package com.gateway.identity.service;

import com.gateway.identity.dto.CertificateResponse;
import com.gateway.identity.dto.UploadCertRequest;
import com.gateway.identity.dto.ValidateCertResponse;
import com.gateway.identity.entity.ApplicationEntity;
import com.gateway.identity.entity.ClientCertificateEntity;
import com.gateway.identity.entity.RevocationListEntity;
import com.gateway.identity.repository.ApplicationRepository;
import com.gateway.identity.repository.ClientCertificateRepository;
import com.gateway.identity.repository.RevocationListRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for client certificate lifecycle management.
 * Handles certificate upload (PEM parsing), listing, revocation, and
 * validation for the gateway runtime mTLS flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final ClientCertificateRepository certRepository;
    private final ApplicationRepository applicationRepository;
    private final RevocationListRepository revocationListRepository;
    private final AuditService auditService;

    private static final int REVOCATION_EXPIRY_HOURS = 24;

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Parse and store a client certificate from PEM format.
     *
     * @param appId   the application ID this certificate belongs to
     * @param request contains the PEM-encoded certificate
     * @return certificate metadata
     */
    @Transactional
    public CertificateResponse uploadCertificate(UUID appId, UploadCertRequest request) {
        ApplicationEntity app = findApplicationOrThrow(appId);

        X509Certificate x509 = parsePem(request.getCertificatePem());

        String subjectCn = extractCn(x509.getSubjectX500Principal().getName());
        String issuer = x509.getIssuerX500Principal().getName();
        Instant expiresAt = x509.getNotAfter().toInstant();
        String fingerprint = computeFingerprint(x509);

        // Check for duplicate fingerprint
        certRepository.findByFingerprint(fingerprint).ifPresent(existing -> {
            throw new IllegalArgumentException("Certificate with this fingerprint already exists: " + existing.getId());
        });

        ClientCertificateEntity entity = ClientCertificateEntity.builder()
                .application(app)
                .subjectCn(subjectCn)
                .fingerprint(fingerprint)
                .issuer(issuer)
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();

        ClientCertificateEntity saved = certRepository.save(entity);
        log.info("Client certificate uploaded: id={}, cn={}, appId={}", saved.getId(), subjectCn, appId);

        auditService.logEvent("certificate.uploaded", "CERTIFICATE", saved.getId().toString(), "SUCCESS");

        return toResponse(saved);
    }

    /**
     * List all certificates for an application.
     *
     * @param appId the application ID
     * @return list of certificate summaries
     */
    @Transactional(readOnly = true)
    public List<CertificateResponse> listCertificates(UUID appId) {
        return certRepository.findByApplicationId(appId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Revoke a certificate. Marks it as REVOKED and adds to the revocation list.
     *
     * @param appId  the application ID
     * @param certId the certificate ID
     */
    @Transactional
    public void revokeCertificate(UUID appId, UUID certId) {
        ClientCertificateEntity cert = certRepository.findById(certId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + certId));

        if (!cert.getApplication().getId().equals(appId)) {
            throw new IllegalArgumentException(
                    "Certificate " + certId + " does not belong to application " + appId);
        }

        cert.setStatus("REVOKED");
        certRepository.save(cert);

        RevocationListEntity entry = RevocationListEntity.builder()
                .revocationType("CLIENT_CERT")
                .credentialId(cert.getFingerprint())
                .reason("Explicitly revoked")
                .revokedAt(Instant.now())
                .expiresAt(Instant.now().plus(REVOCATION_EXPIRY_HOURS, ChronoUnit.HOURS))
                .build();
        revocationListRepository.save(entry);

        log.info("Client certificate revoked: id={}, fingerprint={}, appId={}", certId, cert.getFingerprint(), appId);
        auditService.logEvent("certificate.revoked", "CERTIFICATE", certId.toString(), "SUCCESS");
    }

    /**
     * Validate a client certificate by CN and fingerprint.
     * Called internally by the gateway runtime during mTLS authentication.
     *
     * @param cn          the Subject CN from the client certificate
     * @param fingerprint the SHA-256 fingerprint of the certificate
     * @return application context if valid
     * @throws EntityNotFoundException if no matching active certificate is found
     */
    @Transactional(readOnly = true)
    public ValidateCertResponse validateCert(String cn, String fingerprint) {
        ClientCertificateEntity cert = certRepository.findByFingerprint(fingerprint)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No certificate found with fingerprint: " + fingerprint));

        if (!"ACTIVE".equals(cert.getStatus())) {
            throw new IllegalArgumentException("Certificate is not active, status: " + cert.getStatus());
        }

        if (cert.getExpiresAt() != null && Instant.now().isAfter(cert.getExpiresAt())) {
            throw new IllegalArgumentException("Certificate has expired");
        }

        ApplicationEntity app = cert.getApplication();
        return ValidateCertResponse.builder()
                .applicationId(app.getId())
                .applicationName(app.getName())
                .userId(app.getUser().getId())
                .orgId(app.getOrganization() != null ? app.getOrganization().getId() : null)
                .build();
    }

    /**
     * Validate basic auth credentials by client ID and secret hash.
     * The clientId is the application ID; the secretHash is SHA-256 of the password.
     *
     * @param clientId   the application ID (used as basic auth username)
     * @param secretHash the SHA-256 hash of the basic auth password
     * @return application context if valid
     * @throws EntityNotFoundException  if the application is not found
     * @throws IllegalArgumentException if the secret does not match
     */
    @Transactional(readOnly = true)
    public ValidateCertResponse validateBasicAuth(String clientId, String secretHash) {
        UUID appId;
        try {
            appId = UUID.fromString(clientId);
        } catch (IllegalArgumentException e) {
            throw new EntityNotFoundException("Invalid client ID format: " + clientId);
        }

        ApplicationEntity app = applicationRepository.findById(appId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + clientId));

        if (app.getBasicAuthSecretHash() == null || !app.getBasicAuthSecretHash().equals(secretHash)) {
            throw new IllegalArgumentException("Invalid basic auth credentials");
        }

        return ValidateCertResponse.builder()
                .applicationId(app.getId())
                .applicationName(app.getName())
                .userId(app.getUser().getId())
                .orgId(app.getOrganization() != null ? app.getOrganization().getId() : null)
                .build();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private ApplicationEntity findApplicationOrThrow(UUID appId) {
        return applicationRepository.findById(appId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found: " + appId));
    }

    /**
     * Parse a PEM-encoded X.509 certificate.
     */
    private X509Certificate parsePem(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream inputStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
            return (X509Certificate) factory.generateCertificate(inputStream);
        } catch (CertificateException e) {
            throw new IllegalArgumentException("Failed to parse PEM certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the CN (Common Name) from an X.500 distinguished name string.
     */
    private String extractCn(String dn) {
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return dn; // fallback: return full DN if CN not found
    }

    /**
     * Compute the SHA-256 fingerprint of a certificate (hex-encoded).
     */
    private String computeFingerprint(X509Certificate cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException("Failed to compute certificate fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private CertificateResponse toResponse(ClientCertificateEntity entity) {
        return CertificateResponse.builder()
                .id(entity.getId())
                .applicationId(entity.getApplication().getId())
                .subjectCn(entity.getSubjectCn())
                .fingerprint(entity.getFingerprint())
                .issuer(entity.getIssuer())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
