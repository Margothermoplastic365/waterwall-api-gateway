package com.gateway.management.dto;

import com.gateway.management.entity.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiRequest {

    @NotBlank(message = "API name is required")
    private String name;

    private String contextPath;

    @Builder.Default
    private String version = "1.0.0";

    private String description;

    private List<String> tags;

    private String category;

    private Visibility visibility;

    @Builder.Default
    private String protocolType = "REST";

    private UUID orgId;

    private String backendBaseUrl;
}
