package com.gateway.management.entity;

import com.gateway.management.entity.enums.SubStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscriptions", schema = "gateway",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscription_app_api_env", columnNames = {"application_id", "api_id", "environment_slug"}))
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sub_api"))
    private ApiEntity api;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sub_plan"))
    private PlanEntity plan;

    @Column(name = "environment_slug", nullable = false, length = 50)
    private String environmentSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SubStatus status;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
