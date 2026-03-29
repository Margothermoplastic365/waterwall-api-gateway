package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EffectivePermissionsResponse {

    private UUID userId;

    /** Permissions formatted as "resource:action" strings. */
    private List<String> permissions;

    private List<RoleAssignmentResponse> roleAssignments;
}
