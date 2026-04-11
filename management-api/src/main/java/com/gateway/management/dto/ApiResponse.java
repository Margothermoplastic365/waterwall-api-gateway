package com.gateway.management.dto;

import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.entity.enums.Sensitivity;
import com.gateway.management.entity.enums.VersionStatus;
import com.gateway.management.entity.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {

    private UUID id;
    private String name;
    private String contextPath;
    private String version;
    private String description;
    private ApiStatus status;
    private Visibility visibility;
    private String protocolType;
    private List<String> tags;
    private String category;
    private String authMode;
    private boolean allowAnonymous;
    private String backendBaseUrl;
    private UUID orgId;
    private UUID apiGroupId;
    private String apiGroupName;
    private Sensitivity sensitivity;
    private VersionStatus versionStatus;
    private String deprecatedMessage;
    private UUID successorVersionId;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RouteResponse> routes;
}
