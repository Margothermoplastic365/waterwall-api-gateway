package com.gateway.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {

    @NotNull(message = "Application ID is required")
    private UUID applicationId;

    @NotNull(message = "API ID is required")
    private UUID apiId;

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    private String environmentSlug = "dev";
}
