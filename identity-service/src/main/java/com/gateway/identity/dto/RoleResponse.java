package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.ScopeType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private ScopeType scopeType;
    private boolean isSystem;
    private Instant createdAt;
    private List<PermissionResponse> permissions;
}
