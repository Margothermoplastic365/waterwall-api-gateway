package com.gateway.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "org_members", schema = "identity",
        uniqueConstraints = @UniqueConstraint(name = "uq_org_member", columnNames = {"user_id", "org_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orgmember_user"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orgmember_org"))
    private OrganizationEntity organization;

    @Column(name = "org_role", nullable = false, length = 50)
    private String orgRole;

    @Column(name = "joined_at")
    private Instant joinedAt;
}
