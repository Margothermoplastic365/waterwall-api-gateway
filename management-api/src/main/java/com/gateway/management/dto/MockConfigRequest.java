package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfigRequest {

    private String path;
    private String method;
    private int statusCode;
    private String responseBody;
    private Map<String, String> responseHeaders;
    private int latencyMs;
    private int errorRatePercent;
}
