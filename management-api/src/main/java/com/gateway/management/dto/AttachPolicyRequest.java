package com.gateway.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachPolicyRequest {

    @NotNull(message = "Policy ID is required")
    private UUID policyId;

    private UUID apiId;

    private UUID routeId;

    private String scope;

    private int priority;
}
