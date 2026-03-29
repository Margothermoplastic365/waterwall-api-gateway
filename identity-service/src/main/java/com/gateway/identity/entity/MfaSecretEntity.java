package com.gateway.identity.entity;

import com.gateway.identity.entity.enums.MfaType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mfa_secrets", schema = "identity",
        uniqueConstraints = @UniqueConstraint(name = "uq_mfa_user_type", columnNames = {"user_id", "type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSecretEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mfa_user"))
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MfaType type;

    @Column(name = "secret_encrypted", length = 512)
    private String secretEncrypted;

    @Column(name = "enabled")
    private Boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recovery_codes", columnDefinition = "jsonb")
    private List<String> recoveryCodes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
