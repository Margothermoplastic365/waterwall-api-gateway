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
public class UsageSummaryResponse {

    private long requestsToday;
    private long requestsThisWeek;
    private long requestsThisMonth;
    private double averageLatencyMs;
    private double errorRate;
    private long activeSubscriptions;
    private List<TopApiEntry> topApis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopApiEntry {
        private String apiId;
        private String apiName;
        private long requestCount;
    }
}
