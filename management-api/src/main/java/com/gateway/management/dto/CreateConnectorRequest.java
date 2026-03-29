package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConnectorRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private String config;

    private Boolean enabled;
}
