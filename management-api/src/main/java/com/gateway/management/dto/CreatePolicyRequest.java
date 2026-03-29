package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyRequest {

    @NotBlank(message = "Policy name is required")
    private String name;

    @NotBlank(message = "Policy type is required (RATE_LIMIT, AUTH, TRANSFORM, CACHE, CORS, IP_FILTER)")
    private String type;

    @NotNull(message = "Policy config is required")
    private Map<String, Object> config;

    private String description;
}
