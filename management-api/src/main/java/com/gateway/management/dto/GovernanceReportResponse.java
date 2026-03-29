package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernanceReportResponse {

    private UUID apiId;
    private String apiName;
    private String apiVersion;
    private String status;

    /** Overall governance score 0-100 */
    private int totalScore;

    /** Per-category scores (documentation, security, versioning, lifecycle, design) each 0-100 */
    private Map<String, Integer> scoreBreakdown;

    /** Lint violations found in the spec */
    private List<LintViolation> violations;

    /** Actionable recommendations */
    private List<String> recommendations;

    /** Severity counts */
    private int errorCount;
    private int warningCount;
    private int infoCount;

    private Instant generatedAt;
}
