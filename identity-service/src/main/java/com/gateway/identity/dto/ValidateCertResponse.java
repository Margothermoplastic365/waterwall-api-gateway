package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ValidateCertResponse {

    private UUID applicationId;
    private String applicationName;
    private UUID userId;
    private UUID orgId;
}
