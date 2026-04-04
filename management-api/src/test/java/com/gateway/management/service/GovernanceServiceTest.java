package com.gateway.management.service;

import com.gateway.management.dto.*;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceServiceTest {

    @Mock
    private ApiScoringService apiScoringService;

    @Mock
    private ApiLintingService apiLintingService;

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private ApiSpecRepository apiSpecRepository;

    @InjectMocks
    private GovernanceService governanceService;

    @Test
    void shouldGenerateGovernanceReport() {
        UUID apiId = UUID.randomUUID();
        ApiEntity api = ApiEntity.builder()
                .id(apiId).name("Pet API").version("1.0").status(ApiStatus.PUBLISHED).build();

        ApiSpecEntity spec = ApiSpecEntity.builder()
                .apiId(apiId).specContent("{\"openapi\":\"3.0.0\"}").build();

        ScoreResponse scoreResponse = ScoreResponse.builder()
                .apiId(apiId)
                .totalScore(75)
                .breakdown(Map.of("documentation", 20, "security", 15, "versioning", 15,
                        "lifecycle", 15, "design", 10))
                .recommendations(new ArrayList<>(List.of("Add contact info")))
                .build();

        List<LintViolation> violations = List.of(
                LintViolation.builder().severity("ERROR").rule("operation-description")
                        .path("/pets").message("Missing description").build(),
                LintViolation.builder().severity("WARNING").rule("info-contact")
                        .path("/info").message("No contact info").build(),
                LintViolation.builder().severity("INFO").rule("path-kebab-case")
                        .path("/petStore").message("Use kebab-case").build()
        );

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(apiSpecRepository.findByApiId(apiId)).thenReturn(Optional.of(spec));
        when(apiScoringService.calculateScore(apiId)).thenReturn(scoreResponse);
        when(apiLintingService.lintSpecContent(spec.getSpecContent())).thenReturn(violations);

        GovernanceReportResponse report = governanceService.getApiGovernanceReport(apiId);

        assertThat(report.getApiId()).isEqualTo(apiId);
        assertThat(report.getApiName()).isEqualTo("Pet API");
        assertThat(report.getTotalScore()).isEqualTo(75);
        assertThat(report.getErrorCount()).isEqualTo(1);
        assertThat(report.getWarningCount()).isEqualTo(1);
        assertThat(report.getInfoCount()).isEqualTo(1);
        assertThat(report.getViolations()).hasSize(3);
        assertThat(report.getScoreBreakdown()).containsKey("documentation");
        // Recommendations should include both scoring and lint-based ones
        assertThat(report.getRecommendations()).contains("Add contact info");
        assertThat(report.getRecommendations()).anyMatch(r -> r.contains("descriptions") || r.contains("contact"));
        assertThat(report.getGeneratedAt()).isNotNull();

        verify(apiScoringService).calculateScore(apiId);
        verify(apiLintingService).lintSpecContent(spec.getSpecContent());
    }

    @Test
    void shouldGenerateOverview() {
        UUID api1Id = UUID.randomUUID();
        UUID api2Id = UUID.randomUUID();

        ApiEntity api1 = ApiEntity.builder().id(api1Id).name("API 1").version("1.0").status(ApiStatus.PUBLISHED).build();
        ApiEntity api2 = ApiEntity.builder().id(api2Id).name("API 2").version("2.0").status(ApiStatus.CREATED).build();

        when(apiRepository.findAll()).thenReturn(List.of(api1, api2));

        // Both APIs have specs
        ApiSpecEntity spec1 = ApiSpecEntity.builder().apiId(api1Id).specContent("{\"openapi\":\"3.0.0\"}").build();
        ApiSpecEntity spec2 = ApiSpecEntity.builder().apiId(api2Id).specContent("{\"openapi\":\"3.0.0\"}").build();
        when(apiRepository.findById(api1Id)).thenReturn(Optional.of(api1));
        when(apiRepository.findById(api2Id)).thenReturn(Optional.of(api2));
        when(apiSpecRepository.findByApiId(api1Id)).thenReturn(Optional.of(spec1));
        when(apiSpecRepository.findByApiId(api2Id)).thenReturn(Optional.of(spec2));

        ScoreResponse score1 = ScoreResponse.builder()
                .apiId(api1Id).totalScore(85)
                .breakdown(Map.of("documentation", 25, "security", 20))
                .recommendations(new ArrayList<>(List.of("Add examples")))
                .build();
        ScoreResponse score2 = ScoreResponse.builder()
                .apiId(api2Id).totalScore(45)
                .breakdown(Map.of("documentation", 10, "security", 10))
                .recommendations(new ArrayList<>(List.of("Add examples", "Improve security")))
                .build();

        when(apiScoringService.calculateScore(api1Id)).thenReturn(score1);
        when(apiScoringService.calculateScore(api2Id)).thenReturn(score2);
        when(apiLintingService.lintSpecContent(anyString())).thenReturn(Collections.emptyList());

        GovernanceOverviewResponse overview = governanceService.getGovernanceOverview();

        assertThat(overview.getTotalApis()).isEqualTo(2);
        assertThat(overview.getAverageScore()).isGreaterThan(0);
        assertThat(overview.getScoreDistribution()).containsKeys("excellent", "good", "fair", "poor");
        assertThat(overview.getApiSummaries()).hasSize(2);
        // Sorted by score ascending (worst first)
        assertThat(overview.getApiSummaries().get(0).getScore()).isLessThanOrEqualTo(
                overview.getApiSummaries().get(1).getScore());
        assertThat(overview.getGeneratedAt()).isNotNull();
    }
}
