package com.gateway.management.dto;

import com.gateway.management.entity.enums.SubStatus;
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
public class SubscriptionResponse {

    private UUID id;
    private UUID applicationId;
    private UUID apiId;
    private String apiName;
    private UUID planId;
    private String planName;
    private String environmentSlug;
    private SubStatus status;
    private String reason;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private Instant approvedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
