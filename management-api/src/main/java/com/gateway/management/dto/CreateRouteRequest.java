package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRouteRequest {

    @NotBlank(message = "Route path is required")
    private String path;

    @Builder.Default
    private String method = "GET";

    @NotBlank(message = "Upstream URL is required")
    private String upstreamUrl;

    @Builder.Default
    private List<String> authTypes = List.of("API_KEY", "OAUTH2_JWT");

    private int priority;

    private boolean stripPrefix;
}
