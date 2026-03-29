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
public class GatewayNodeResponse {

    private UUID id;
    private String hostname;
    private String ipAddress;
    private Integer port;
    private Long configVersion;
    private String status;
    private Instant lastHeartbeat;
    private Instant registeredAt;
}
