package com.gateway.analytics.service;

import com.gateway.analytics.dto.SlaConfigRequest;
import com.gateway.analytics.dto.SlaConfigResponse;
import com.gateway.analytics.entity.SlaBreachEntity;
import com.gateway.analytics.entity.SlaConfigEntity;
import com.gateway.analytics.repository.SlaBreachRepository;
import com.gateway.analytics.repository.SlaConfigRepository;
import com.gateway.analytics.store.RequestLogStore;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaMonitoringServiceTest {

    @Mock
    private SlaConfigRepository slaConfigRepository;

    @Mock
    private SlaBreachRepository slaBreachRepository;

    @Mock
    private RequestLogStore requestLogStore;

    @InjectMocks
    private SlaMonitoringService slaMonitoringService;

    // ── evaluateSlas ────────────────────────────────────────────────────

    @Test
    void evaluateSlas_recordsUptimeBreach_whenBelowTarget() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId)
                .apiId(apiId)
                .apiName("TestAPI")
                .uptimeTarget(BigDecimal.valueOf(99.9))
                .enabled(true)
                .build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric("uptime", 5, apiId)).thenReturn(BigDecimal.valueOf(95.0));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(configId, "UPTIME"))
                .thenReturn(List.of());

        slaMonitoringService.evaluateSlas();

        ArgumentCaptor<SlaBreachEntity> captor = ArgumentCaptor.forClass(SlaBreachEntity.class);
        verify(slaBreachRepository).save(captor.capture());
        SlaBreachEntity breach = captor.getValue();
        assertThat(breach.getApiId()).isEqualTo(apiId);
        assertThat(breach.getMetric()).isEqualTo("UPTIME");
        assertThat(breach.getActualValue()).isEqualByComparingTo(BigDecimal.valueOf(95.0));
        assertThat(breach.getTargetValue()).isEqualByComparingTo(BigDecimal.valueOf(99.9));
    }

    @Test
    void evaluateSlas_doesNotDuplicateBreach_whenOpenBreachExists() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId).apiId(apiId).apiName("API")
                .uptimeTarget(BigDecimal.valueOf(99.9)).enabled(true).build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric("uptime", 5, apiId)).thenReturn(BigDecimal.valueOf(90.0));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(configId, "UPTIME"))
                .thenReturn(List.of(SlaBreachEntity.builder().build()));

        slaMonitoringService.evaluateSlas();

        verify(slaBreachRepository, never()).save(any());
    }

    @Test
    void evaluateSlas_resolvesOpenBreaches_whenMetricRecovers() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId).apiId(apiId).apiName("API")
                .uptimeTarget(BigDecimal.valueOf(99.0)).enabled(true).build();

        SlaBreachEntity openBreach = SlaBreachEntity.builder()
                .id(UUID.randomUUID()).apiId(apiId).slaConfigId(configId)
                .metric("UPTIME").build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric("uptime", 5, apiId)).thenReturn(BigDecimal.valueOf(99.5));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(configId, "UPTIME"))
                .thenReturn(List.of(openBreach));

        slaMonitoringService.evaluateSlas();

        verify(slaBreachRepository).save(openBreach);
        assertThat(openBreach.getResolvedAt()).isNotNull();
    }

    @Test
    void evaluateSlas_recordsLatencyBreach_whenP95ExceedsTarget() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId).apiId(apiId).apiName("API")
                .latencyTargetMs(200).enabled(true).build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric("latency_p95", 5, apiId)).thenReturn(BigDecimal.valueOf(350));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(configId, "LATENCY_P95"))
                .thenReturn(List.of());

        slaMonitoringService.evaluateSlas();

        ArgumentCaptor<SlaBreachEntity> captor = ArgumentCaptor.forClass(SlaBreachEntity.class);
        verify(slaBreachRepository).save(captor.capture());
        assertThat(captor.getValue().getMetric()).isEqualTo("LATENCY_P95");
    }

    @Test
    void evaluateSlas_recordsErrorRateBreach_whenExceedsBudget() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId).apiId(apiId).apiName("API")
                .errorBudgetPct(BigDecimal.valueOf(1.0)).enabled(true).build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric("error_rate", 5, apiId)).thenReturn(BigDecimal.valueOf(3.5));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(configId, "ERROR_RATE"))
                .thenReturn(List.of());

        slaMonitoringService.evaluateSlas();

        ArgumentCaptor<SlaBreachEntity> captor = ArgumentCaptor.forClass(SlaBreachEntity.class);
        verify(slaBreachRepository).save(captor.capture());
        assertThat(captor.getValue().getMetric()).isEqualTo("ERROR_RATE");
    }

    @Test
    void evaluateSlas_noBreachWhenMetricDataIsNull() {
        UUID apiId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        SlaConfigEntity config = SlaConfigEntity.builder()
                .id(configId).apiId(apiId).apiName("API")
                .uptimeTarget(BigDecimal.valueOf(99.0))
                .latencyTargetMs(200)
                .errorBudgetPct(BigDecimal.ONE)
                .enabled(true).build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(requestLogStore.queryMetric(anyString(), eq(5), eq(apiId))).thenReturn(null);

        slaMonitoringService.evaluateSlas();

        // Null metrics should resolve open breaches, not create new ones
        verify(slaBreachRepository, never()).save(argThat(b -> b.getBreachedAt() != null && b.getResolvedAt() == null));
    }

    @Test
    void evaluateSlas_continuesAfterExceptionInOneConfig() {
        SlaConfigEntity config1 = SlaConfigEntity.builder()
                .id(UUID.randomUUID()).apiId(UUID.randomUUID()).apiName("A1")
                .uptimeTarget(BigDecimal.valueOf(99.0)).enabled(true).build();
        SlaConfigEntity config2 = SlaConfigEntity.builder()
                .id(UUID.randomUUID()).apiId(UUID.randomUUID()).apiName("A2")
                .uptimeTarget(BigDecimal.valueOf(99.0)).enabled(true).build();

        when(slaConfigRepository.findByEnabledTrue()).thenReturn(List.of(config1, config2));
        when(requestLogStore.queryMetric("uptime", 5, config1.getApiId()))
                .thenThrow(new RuntimeException("fail"));
        when(requestLogStore.queryMetric("uptime", 5, config2.getApiId()))
                .thenReturn(BigDecimal.valueOf(95.0));
        when(slaBreachRepository.findBySlaConfigIdAndMetricAndResolvedAtIsNull(config2.getId(), "UPTIME"))
                .thenReturn(List.of());

        slaMonitoringService.evaluateSlas();

        // Second config should still be evaluated
        verify(slaBreachRepository).save(any(SlaBreachEntity.class));
    }

    // ── CRUD ────────────────────────────────────────────────────────────

    @Test
    void createConfig_savesAndReturnsResponse() {
        UUID apiId = UUID.randomUUID();
        SlaConfigRequest request = SlaConfigRequest.builder()
                .apiId(apiId).apiName("TestAPI")
                .uptimeTarget(BigDecimal.valueOf(99.9))
                .latencyTargetMs(200)
                .errorBudgetPct(BigDecimal.valueOf(1.0))
                .enabled(true).build();

        when(slaConfigRepository.save(any())).thenAnswer(inv -> {
            SlaConfigEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SlaConfigResponse result = slaMonitoringService.createConfig(request);

        assertThat(result.getApiId()).isEqualTo(apiId);
        assertThat(result.getApiName()).isEqualTo("TestAPI");
        assertThat(result.getUptimeTarget()).isEqualByComparingTo(BigDecimal.valueOf(99.9));
    }

    @Test
    void createConfig_defaultsEnabledToTrue_whenNull() {
        SlaConfigRequest request = SlaConfigRequest.builder()
                .apiId(UUID.randomUUID()).apiName("API").build();

        when(slaConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SlaConfigResponse result = slaMonitoringService.createConfig(request);

        assertThat(result.getEnabled()).isTrue();
    }

    @Test
    void getConfig_returnsResponse() {
        UUID id = UUID.randomUUID();
        SlaConfigEntity entity = SlaConfigEntity.builder()
                .id(id).apiId(UUID.randomUUID()).apiName("API").build();
        when(slaConfigRepository.findById(id)).thenReturn(Optional.of(entity));

        SlaConfigResponse result = slaMonitoringService.getConfig(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void getConfig_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(slaConfigRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slaMonitoringService.getConfig(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteConfig_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(slaConfigRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> slaMonitoringService.deleteConfig(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteConfig_deletesExistingConfig() {
        UUID id = UUID.randomUUID();
        when(slaConfigRepository.existsById(id)).thenReturn(true);

        slaMonitoringService.deleteConfig(id);

        verify(slaConfigRepository).deleteById(id);
    }

    @Test
    void listConfigs_returnsAll() {
        SlaConfigEntity e1 = SlaConfigEntity.builder().id(UUID.randomUUID()).apiName("A1").build();
        SlaConfigEntity e2 = SlaConfigEntity.builder().id(UUID.randomUUID()).apiName("A2").build();
        when(slaConfigRepository.findAll()).thenReturn(List.of(e1, e2));

        List<SlaConfigResponse> result = slaMonitoringService.listConfigs();

        assertThat(result).hasSize(2);
    }

    // ── listBreaches ────────────────────────────────────────────────────

    @Test
    void listBreaches_withApiId_filtersResults() {
        UUID apiId = UUID.randomUUID();
        SlaBreachEntity breach = SlaBreachEntity.builder()
                .id(UUID.randomUUID()).apiId(apiId).metric("UPTIME").build();
        when(slaBreachRepository.findByApiIdAndBreachedAtAfterOrderByBreachedAtDesc(eq(apiId), any()))
                .thenReturn(List.of(breach));

        var result = slaMonitoringService.listBreaches(apiId, "24h");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApiId()).isEqualTo(apiId);
    }

    @Test
    void listBreaches_withoutApiId_returnsAll() {
        when(slaBreachRepository.findByBreachedAtAfterOrderByBreachedAtDesc(any()))
                .thenReturn(List.of());

        var result = slaMonitoringService.listBreaches(null, "7d");

        assertThat(result).isEmpty();
        verify(slaBreachRepository).findByBreachedAtAfterOrderByBreachedAtDesc(any());
    }

    @Test
    void listBreaches_defaultsTo24h_whenRangeIsNull() {
        when(slaBreachRepository.findByBreachedAtAfterOrderByBreachedAtDesc(any()))
                .thenReturn(List.of());

        slaMonitoringService.listBreaches(null, null);

        verify(slaBreachRepository).findByBreachedAtAfterOrderByBreachedAtDesc(any());
    }

    @Test
    void listBreaches_parsesMinuteRange() {
        when(slaBreachRepository.findByBreachedAtAfterOrderByBreachedAtDesc(any()))
                .thenReturn(List.of());

        slaMonitoringService.listBreaches(null, "30m");

        verify(slaBreachRepository).findByBreachedAtAfterOrderByBreachedAtDesc(any());
    }
}
