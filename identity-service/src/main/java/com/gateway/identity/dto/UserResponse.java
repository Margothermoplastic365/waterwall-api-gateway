package com.gateway.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;

    private String email;

    private boolean emailVerified;

    private String status;

    private Instant createdAt;

    private Instant lastLoginAt;

    private ProfileResponse profile;
}
