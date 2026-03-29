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
public class ApiDeploymentResponse {

    private UUID deploymentId;
    private UUID apiId;
    private String apiName;
    private String environment;
    private String status;
    private String upstreamUrl;
    private Instant deployedAt;
    private String deployedBy;
}
