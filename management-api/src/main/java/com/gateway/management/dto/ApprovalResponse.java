package com.gateway.management.dto;

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
public class ApprovalResponse {

    private UUID id;
    private String type;
    private UUID resourceId;
    private String status;
    private UUID requestedBy;
    private UUID submittedBy;
    private UUID approvedBy;
    private String rejectedReason;
    private Integer currentLevel;
    private Integer maxLevel;
    private Instant cooldownUntil;
    private Instant requestedAt;
    private Instant resolvedAt;
}
