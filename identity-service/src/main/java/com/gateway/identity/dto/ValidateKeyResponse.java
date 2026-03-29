package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ValidateKeyResponse {

    private UUID applicationId;
    private String applicationName;
    private UUID userId;
    private UUID orgId;
    private String status;
    private String environmentSlug;
}
