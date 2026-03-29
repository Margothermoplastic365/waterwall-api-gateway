package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.ScopeType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AssignRoleRequest {

    @NotNull(message = "Role ID is required")
    private UUID roleId;

    @NotNull(message = "Scope type is required")
    private ScopeType scopeType;

    /** Nullable — null for GLOBAL scope. */
    private UUID scopeId;

    /** Nullable — null for permanent assignment. */
    private Instant expiresAt;
}
