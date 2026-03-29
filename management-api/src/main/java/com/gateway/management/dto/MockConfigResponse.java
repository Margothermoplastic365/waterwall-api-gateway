package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfigResponse {

    private UUID id;
    private UUID apiId;
    private String path;
    private String method;
    private int statusCode;
    private String responseBody;
    private Map<String, String> responseHeaders;
    private int latencyMs;
    private int errorRatePercent;
    private boolean mockEnabled;
    private Instant createdAt;
    private Instant updatedAt;
}
