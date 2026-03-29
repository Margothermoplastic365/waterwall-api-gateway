package com.gateway.management.service;

import com.gateway.management.dto.*;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates API governance by combining scoring and linting into unified reports.
 *
 * <ul>
 *   <li>{@link #getApiGovernanceReport(UUID)} - full report for a single API: score + violations + recommendations</li>
 *   <li>{@link #getGovernanceOverview()} - aggregate scores and violation stats across all APIs</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovernanceService {

    private final ApiScoringService apiScoringService;
    private final ApiLintingService apiLintingService;
    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;

    // ── Single API governance report ────────────────────────────────────

    @Transactional
    public GovernanceReportResponse getApiGovernanceReport(UUID apiId) {
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        // Calculate score
        ScoreResponse scoreResponse = apiScoringService.calculateScore(apiId);

        // Run linting
        List<LintViolation> violations;
        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId).orElse(null);
        if (spec != null && spec.getSpecContent() != null && !spec.getSpecContent().isBlank()) {
            violations = apiLintingService.lintSpecContent(spec.getSpecContent());
        } else {
            violations = List.of(LintViolation.builder()
                    .severity("ERROR")
                    .rule("spec-not-empty")
                    .path("/")
                    .message("No API spec uploaded; linting requires a spec")
                    .build());
        }

        // Count by severity
        long errors = violations.stream().filter(v -> "ERROR".equals(v.getSeverity())).count();
        long warnings = violations.stream().filter(v -> "WARNING".equals(v.getSeverity())).count();
        long infos = violations.stream().filter(v -> "INFO".equals(v.getSeverity())).count();

        // Merge recommendations: scoring recommendations plus violation-based advice
        List<String> recommendations = new ArrayList<>(scoreResponse.getRecommendations());
        addLintBasedRecommendations(violations, recommendations);

        log.info("Generated governance report for API {}: score={}, violations={}",
                apiId, scoreResponse.getTotalScore(), violations.size());

        return GovernanceReportResponse.builder()
                .apiId(apiId)
                .apiName(api.getName())
                .apiVersion(api.getVersion())
                .status(api.getStatus() != null ? api.getStatus().name() : "UNKNOWN")
                .totalScore(scoreResponse.getTotalScore())
                .scoreBreakdown(scoreResponse.getBreakdown())
                .violations(violations)
                .recommendations(recommendations)
                .errorCount((int) errors)
                .warningCount((int) warnings)
                .infoCount((int) infos)
                .generatedAt(Instant.now())
                .build();
    }

    // ── Aggregate governance overview ───────────────────────────────────

    @Transactional
    public GovernanceOverviewResponse getGovernanceOverview() {
        List<ApiEntity> apis = apiRepository.findAll();

        if (apis.isEmpty()) {
            return GovernanceOverviewResponse.builder()
                    .totalApis(0)
                    .averageScore(0)
                    .medianScore(0)
                    .scoreDistribution(Map.of("excellent", 0, "good", 0, "fair", 0, "poor", 0))
                    .averageCategoryScores(Collections.emptyMap())
                    .totalViolationsBySeverity(Map.of("ERROR", 0, "WARNING", 0, "INFO", 0))
                    .topRecommendations(Collections.emptyList())
                    .apiSummaries(Collections.emptyList())
                    .generatedAt(Instant.now())
                    .build();
        }

        List<GovernanceReportResponse> reports = new ArrayList<>();
        for (ApiEntity api : apis) {
            try {
                reports.add(getApiGovernanceReport(api.getId()));
            } catch (Exception e) {
                log.warn("Failed to generate governance report for API {}: {}",
                        api.getId(), e.getMessage());
            }
        }

        if (reports.isEmpty()) {
            return GovernanceOverviewResponse.builder()
                    .totalApis(apis.size())
                    .averageScore(0)
                    .medianScore(0)
                    .scoreDistribution(Map.of("excellent", 0, "good", 0, "fair", 0, "poor", 0))
                    .averageCategoryScores(Collections.emptyMap())
                    .totalViolationsBySeverity(Map.of("ERROR", 0, "WARNING", 0, "INFO", 0))
                    .topRecommendations(Collections.emptyList())
                    .apiSummaries(Collections.emptyList())
                    .generatedAt(Instant.now())
                    .build();
        }

        // Scores
        List<Integer> scores = reports.stream()
                .map(GovernanceReportResponse::getTotalScore)
                .sorted()
                .toList();

        double averageScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double medianScore = computeMedian(scores);

        // Distribution
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("excellent", (int) scores.stream().filter(s -> s >= 80).count());
        distribution.put("good", (int) scores.stream().filter(s -> s >= 60 && s < 80).count());
        distribution.put("fair", (int) scores.stream().filter(s -> s >= 40 && s < 60).count());
        distribution.put("poor", (int) scores.stream().filter(s -> s < 40).count());

        // Average category scores
        Map<String, Double> avgCategoryScores = computeAverageCategoryScores(reports);

        // Total violations by severity
        int totalErrors = reports.stream().mapToInt(GovernanceReportResponse::getErrorCount).sum();
        int totalWarnings = reports.stream().mapToInt(GovernanceReportResponse::getWarningCount).sum();
        int totalInfos = reports.stream().mapToInt(GovernanceReportResponse::getInfoCount).sum();
        Map<String, Integer> violationsBySeverity = new LinkedHashMap<>();
        violationsBySeverity.put("ERROR", totalErrors);
        violationsBySeverity.put("WARNING", totalWarnings);
        violationsBySeverity.put("INFO", totalInfos);

        // Top recommendations (most frequent across all APIs)
        List<String> topRecommendations = computeTopRecommendations(reports, 10);

        // API summaries sorted by score ascending (worst first)
        List<GovernanceOverviewResponse.ApiScoreSummary> summaries = reports.stream()
                .map(r -> GovernanceOverviewResponse.ApiScoreSummary.builder()
                        .apiId(r.getApiId())
                        .apiName(r.getApiName())
                        .status(r.getStatus())
                        .score(r.getTotalScore())
                        .violationCount(r.getViolations() != null ? r.getViolations().size() : 0)
                        .build())
                .sorted(Comparator.comparingInt(GovernanceOverviewResponse.ApiScoreSummary::getScore))
                .toList();

        log.info("Generated governance overview: {} APIs, avg score={}", reports.size(), String.format("%.1f", averageScore));

        return GovernanceOverviewResponse.builder()
                .totalApis(reports.size())
                .averageScore(Math.round(averageScore * 10.0) / 10.0)
                .medianScore(Math.round(medianScore * 10.0) / 10.0)
                .scoreDistribution(distribution)
                .averageCategoryScores(avgCategoryScores)
                .totalViolationsBySeverity(violationsBySeverity)
                .topRecommendations(topRecommendations)
                .apiSummaries(summaries)
                .generatedAt(Instant.now())
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void addLintBasedRecommendations(List<LintViolation> violations, List<String> recommendations) {
        Set<String> existingRules = new HashSet<>();
        for (LintViolation v : violations) {
            if (existingRules.add(v.getRule())) {
                String rec = switch (v.getRule()) {
                    case "path-starts-with-slash" -> "Ensure all paths begin with '/'";
                    case "operation-description" -> "Add descriptions or summaries to all operations";
                    case "operation-responses" -> "Define response schemas for all operations";
                    case "path-param-documented" -> "Document all path parameters in the parameters section";
                    case "info-contact" -> "Add contact information to the spec info section";
                    case "info-license" -> "Add license information to the spec";
                    case "path-kebab-case" -> "Use lowercase kebab-case for URL paths";
                    case "operation-operationId" -> "Add operationId to all operations for SDK generation";
                    case "security-scheme-defined" -> "Define at least one security scheme";
                    case "global-security" -> "Set global security requirements on the API";
                    case "error-response-schema" -> "Include error response schemas (4xx/5xx) in operations";
                    default -> null;
                };
                if (rec != null && !recommendations.contains(rec)) {
                    recommendations.add(rec);
                }
            }
        }
    }

    private double computeMedian(List<Integer> sortedScores) {
        if (sortedScores.isEmpty()) return 0;
        int size = sortedScores.size();
        if (size % 2 == 0) {
            return (sortedScores.get(size / 2 - 1) + sortedScores.get(size / 2)) / 2.0;
        }
        return sortedScores.get(size / 2);
    }

    private Map<String, Double> computeAverageCategoryScores(List<GovernanceReportResponse> reports) {
        Map<String, List<Integer>> categoryScores = new LinkedHashMap<>();

        for (GovernanceReportResponse report : reports) {
            if (report.getScoreBreakdown() != null) {
                report.getScoreBreakdown().forEach((category, score) ->
                        categoryScores.computeIfAbsent(category, k -> new ArrayList<>()).add(score));
            }
        }

        Map<String, Double> averages = new LinkedHashMap<>();
        categoryScores.forEach((category, scores) -> {
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            averages.put(category, Math.round(avg * 10.0) / 10.0);
        });

        return averages;
    }

    private List<String> computeTopRecommendations(List<GovernanceReportResponse> reports, int limit) {
        Map<String, Long> frequency = reports.stream()
                .filter(r -> r.getRecommendations() != null)
                .flatMap(r -> r.getRecommendations().stream())
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
