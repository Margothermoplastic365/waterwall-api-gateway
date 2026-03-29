package com.gateway.identity.entity;

import com.gateway.identity.entity.enums.ScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_assignments", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_roleassign_user"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_roleassign_role"))
    private RoleEntity role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;
}
