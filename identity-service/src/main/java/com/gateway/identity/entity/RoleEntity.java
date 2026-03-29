package com.gateway.identity.entity;

import com.gateway.identity.entity.enums.ScopeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "permissions")
@ToString(exclude = "permissions")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    @Column(name = "is_system")
    private Boolean isSystem;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            schema = "identity",
            joinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_roleperm_role")),
            inverseJoinColumns = @JoinColumn(name = "permission_id",
                    foreignKey = @ForeignKey(name = "fk_roleperm_perm"))
    )
    @Builder.Default
    private Set<PermissionEntity> permissions = new HashSet<>();
}
