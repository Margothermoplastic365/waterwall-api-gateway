package com.gateway.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revocation_list", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevocationListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "revocation_type", nullable = false, length = 20)
    private String revocationType;

    @Column(name = "credential_id", nullable = false, length = 255)
    private String credentialId;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;
}
