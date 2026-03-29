package com.gateway.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrgRequest {

    @NotBlank(message = "Organization name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Domain is required")
    private String domain;
}
