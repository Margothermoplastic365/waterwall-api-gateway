package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageHistoryResponse {

    private String range;
    private List<DailyUsage> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyUsage {
        private LocalDate date;
        private long requestCount;
        private long errorCount;
        private double averageLatencyMs;
    }
}
