package com.gateway.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateApplicationRequest {

    @NotBlank(message = "Application name is required")
    private String name;

    private String description;

    private List<String> callbackUrls;
}
