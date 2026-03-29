package com.gateway.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_certificates", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_clientcert_app"))
    private ApplicationEntity application;

    @Column(name = "subject_cn", nullable = false, length = 255)
    private String subjectCn;

    @Column(name = "fingerprint", nullable = false, unique = true, length = 255)
    private String fingerprint;

    @Column(name = "issuer", length = 500)
    private String issuer;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
