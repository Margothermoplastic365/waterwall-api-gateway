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
public class CreateVersionRequest {

    @NotNull(message = "Source version ID is required")
    private UUID sourceVersionId;

    @NotBlank(message = "New version string is required")
    private String newVersion;
}
