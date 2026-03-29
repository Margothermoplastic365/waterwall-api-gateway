package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvitationResponse {

    private UUID id;
    private String email;
    private UUID orgId;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
}
