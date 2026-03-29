package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    private String name;

    private String description;

    @NotNull(message = "Scope type is required")
    private ScopeType scopeType;

    private List<UUID> permissionIds;
}
