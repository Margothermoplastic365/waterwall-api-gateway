package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrgMemberResponse {

    private UUID id;
    private UUID userId;
    private String email;
    private String displayName;
    private String orgRole;
    private Instant joinedAt;
}
