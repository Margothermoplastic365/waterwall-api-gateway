package com.gateway.management.dto;

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
public class MigrationResponse {

    private UUID migrationId;
    private UUID apiId;
    private String sourceEnv;
    private String targetEnv;
    private String status;
    private Instant initiatedAt;
    private String initiatedBy;
}
