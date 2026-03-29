package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageBreakdownResponse {

    private List<ApiUsageEntry> apis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiUsageEntry {
        private String apiId;
        private String apiName;
        private long totalRequests;
        private double averageLatencyMs;
        private long errorCount;
        private double errorRate;
    }
}
