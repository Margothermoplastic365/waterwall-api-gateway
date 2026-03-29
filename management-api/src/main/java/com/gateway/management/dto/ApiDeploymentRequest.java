package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiDeploymentRequest {

    @NotNull
    private UUID apiId;

    @NotBlank
    private String targetEnvironment;

    private String upstreamUrl;
}
