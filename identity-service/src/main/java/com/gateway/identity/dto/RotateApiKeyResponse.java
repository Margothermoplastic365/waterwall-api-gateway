package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RotateApiKeyResponse {

    private UUID newKeyId;
    private String newKeyPrefix;
    private String newFullKey;
    private UUID oldKeyId;
    private String oldKeyPrefix;
    private Instant oldKeyExpiresAt;
}
