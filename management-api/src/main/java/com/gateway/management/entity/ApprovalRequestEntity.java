package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_requests", schema = "gateway")
public class ApprovalRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approval_level")
    @Builder.Default
    private Integer approvalLevel = 1;

    @Column(name = "current_level")
    @Builder.Default
    private Integer currentLevel = 1;

    @Column(name = "max_level")
    @Builder.Default
    private Integer maxLevel = 1;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_reason", columnDefinition = "text")
    private String rejectedReason;

    @Column(name = "requested_at")
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
