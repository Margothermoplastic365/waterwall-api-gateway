package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.ApiKeyStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private String environmentSlug;
    private ApiKeyStatus status;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
