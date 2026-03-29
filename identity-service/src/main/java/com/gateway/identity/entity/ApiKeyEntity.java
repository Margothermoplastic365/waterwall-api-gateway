package com.gateway.identity.entity;

import com.gateway.identity.entity.enums.ApiKeyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_apikey_app"))
    private ApplicationEntity application;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;

    @Column(name = "environment_slug", nullable = false, length = 50)
    @Builder.Default
    private String environmentSlug = "dev";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
