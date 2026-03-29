package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.ScopeType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RoleAssignmentResponse {

    private UUID id;
    private UUID roleId;
    private String roleName;
    private ScopeType scopeType;
    private UUID scopeId;
    private Instant expiresAt;
    private Instant assignedAt;
    private UUID assignedBy;
}
