package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CertificateResponse {

    private UUID id;
    private UUID applicationId;
    private String subjectCn;
    private String fingerprint;
    private String issuer;
    private Instant expiresAt;
    private String status;
    private Instant createdAt;
}
