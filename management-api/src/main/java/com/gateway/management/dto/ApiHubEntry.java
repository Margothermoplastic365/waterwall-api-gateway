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
public class ApiHubEntry {

    private UUID apiId;
    private String name;
    private String version;
    private String status;
    private int subscriberCount;
    private int dependencyCount;
    private boolean isStale;
    private boolean isOrphan;
    private Instant lastUpdated;
}
