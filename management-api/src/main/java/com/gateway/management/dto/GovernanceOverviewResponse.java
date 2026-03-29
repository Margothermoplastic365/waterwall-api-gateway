package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernanceOverviewResponse {

    /** Total number of APIs assessed */
    private int totalApis;

    /** Average governance score across all APIs */
    private double averageScore;

    /** Median governance score */
    private double medianScore;

    /** Score distribution: e.g., "excellent" (80-100), "good" (60-79), "fair" (40-59), "poor" (0-39) */
    private Map<String, Integer> scoreDistribution;

    /** Average per-category scores */
    private Map<String, Double> averageCategoryScores;

    /** Total violations across all APIs by severity */
    private Map<String, Integer> totalViolationsBySeverity;

    /** Top recommendations across the portfolio (most common) */
    private List<String> topRecommendations;

    /** Individual API summaries, sorted by score ascending (worst first) */
    private List<ApiScoreSummary> apiSummaries;

    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiScoreSummary {
        private java.util.UUID apiId;
        private String apiName;
        private String status;
        private int score;
        private int violationCount;
    }
}
