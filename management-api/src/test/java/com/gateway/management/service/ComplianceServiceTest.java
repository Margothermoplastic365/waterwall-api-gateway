package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.entity.ComplianceReportEntity;
import com.gateway.management.entity.ConsentRecordEntity;
import com.gateway.management.entity.DataClassificationEntity;
import com.gateway.management.repository.ComplianceReportRepository;
import com.gateway.management.repository.ConsentRecordRepository;
import com.gateway.management.repository.DataClassificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock
    private ComplianceReportRepository complianceReportRepository;

    @Mock
    private DataClassificationRepository dataClassificationRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ComplianceService complianceService;

    @Test
    void shouldGenerateReport() {
        when(complianceReportRepository.save(any(ComplianceReportEntity.class)))
                .thenAnswer(inv -> {
                    ComplianceReportEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        ComplianceReportEntity report = complianceService.generateReport("SOC2");

        assertThat(report.getType()).isEqualTo("SOC2");
        assertThat(report.getStatus()).isEqualTo("COMPLETED");
        assertThat(report.getScore()).isEqualTo(100);
        assertThat(report.getFindings()).isNotNull();
        assertThat(report.getGeneratedAt()).isNotNull();
        verify(complianceReportRepository).save(any(ComplianceReportEntity.class));
    }

    @Test
    void shouldGenerateGdprReportWithReducedScoreWhenNoConsent() {
        when(consentRecordRepository.count()).thenReturn(0L);
        when(dataClassificationRepository.count()).thenReturn(0L);
        when(complianceReportRepository.save(any(ComplianceReportEntity.class)))
                .thenAnswer(inv -> {
                    ComplianceReportEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        ComplianceReportEntity report = complianceService.generateReport("GDPR");

        assertThat(report.getType()).isEqualTo("GDPR");
        // Score reduced by 20 (no consent) + 15 (no classification) = 65
        assertThat(report.getScore()).isEqualTo(65);
    }

    @Test
    void shouldClassifyApiData() {
        UUID apiId = UUID.randomUUID();
        when(dataClassificationRepository.findByApiId(apiId)).thenReturn(Optional.empty());
        when(dataClassificationRepository.save(any(DataClassificationEntity.class)))
                .thenAnswer(inv -> {
                    DataClassificationEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        DataClassificationEntity result = complianceService.classifyApiData(
                apiId, "CONFIDENTIAL", "[\"email\",\"name\"]", "{\"retention\":\"90d\"}");

        assertThat(result.getApiId()).isEqualTo(apiId);
        assertThat(result.getClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(result.getPiiFields()).isEqualTo("[\"email\",\"name\"]");
        assertThat(result.getHandlingPolicy()).isEqualTo("{\"retention\":\"90d\"}");
        verify(dataClassificationRepository).save(any(DataClassificationEntity.class));
    }

    @Test
    void shouldClassifyApiDataUpsertExisting() {
        UUID apiId = UUID.randomUUID();
        DataClassificationEntity existing = DataClassificationEntity.builder()
                .id(UUID.randomUUID()).apiId(apiId).classification("PUBLIC").build();

        when(dataClassificationRepository.findByApiId(apiId)).thenReturn(Optional.of(existing));
        when(dataClassificationRepository.save(any(DataClassificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DataClassificationEntity result = complianceService.classifyApiData(
                apiId, "RESTRICTED", "[\"ssn\"]", "{\"retention\":\"30d\"}");

        assertThat(result.getClassification()).isEqualTo("RESTRICTED");
        assertThat(result.getId()).isEqualTo(existing.getId());
    }

    @Test
    void shouldRecordConsent() {
        UUID userId = UUID.randomUUID();
        when(consentRecordRepository.save(any(ConsentRecordEntity.class)))
                .thenAnswer(inv -> {
                    ConsentRecordEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        ConsentRecordEntity result = complianceService.recordConsent(userId, "analytics", true);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPurpose()).isEqualTo("analytics");
        assertThat(result.getGranted()).isTrue();
        assertThat(result.getGrantedAt()).isNotNull();
        verify(consentRecordRepository).save(any(ConsentRecordEntity.class));
    }

    @Test
    void shouldRecordConsentDenied() {
        UUID userId = UUID.randomUUID();
        when(consentRecordRepository.save(any(ConsentRecordEntity.class)))
                .thenAnswer(inv -> {
                    ConsentRecordEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        ConsentRecordEntity result = complianceService.recordConsent(userId, "marketing", false);

        assertThat(result.getGranted()).isFalse();
        assertThat(result.getGrantedAt()).isNull();
    }

    @Test
    void shouldExportUserData() {
        UUID userId = UUID.randomUUID();
        ConsentRecordEntity consent = ConsentRecordEntity.builder()
                .id(UUID.randomUUID()).userId(userId).purpose("analytics").granted(true).build();

        when(consentRecordRepository.findByUserId(userId)).thenReturn(List.of(consent));

        Map<String, Object> export = complianceService.exportUserData(userId);

        assertThat(export).containsKey("userId");
        assertThat(export.get("userId")).isEqualTo(userId.toString());
        assertThat(export).containsKey("exportedAt");
        assertThat(export).containsKey("consentRecords");
        @SuppressWarnings("unchecked")
        List<ConsentRecordEntity> records = (List<ConsentRecordEntity>) export.get("consentRecords");
        assertThat(records).hasSize(1);
    }

    @Test
    void shouldDeleteUserData() {
        UUID userId = UUID.randomUUID();
        ConsentRecordEntity consent1 = ConsentRecordEntity.builder()
                .id(UUID.randomUUID()).userId(userId).purpose("analytics").build();
        ConsentRecordEntity consent2 = ConsentRecordEntity.builder()
                .id(UUID.randomUUID()).userId(userId).purpose("marketing").build();

        when(consentRecordRepository.findByUserId(userId)).thenReturn(List.of(consent1, consent2));

        Map<String, Object> result = complianceService.deleteUserData(userId);

        assertThat(result.get("userId")).isEqualTo(userId.toString());
        assertThat(result.get("consentRecordsDeleted")).isEqualTo(2);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        verify(consentRecordRepository).deleteAll(List.of(consent1, consent2));
    }
}
