package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private UUID id;
    private String path;
    private String method;
    private String upstreamUrl;
    private List<String> authTypes;
    private int priority;
    private boolean stripPrefix;
    private boolean enabled;
    private boolean requireMfa;
    private Instant createdAt;
}
