package com.gateway.analytics.service;

import com.gateway.analytics.dto.AlertHistoryResponse;
import com.gateway.analytics.dto.AlertRuleRequest;
import com.gateway.analytics.dto.AlertRuleResponse;
import com.gateway.analytics.entity.AlertHistoryEntity;
import com.gateway.analytics.entity.AlertRuleEntity;
import com.gateway.analytics.repository.AlertHistoryRepository;
import com.gateway.analytics.repository.AlertRuleRepository;
import com.gateway.analytics.store.RequestLogStore;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertingServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private RequestLogStore requestLogStore;

    @InjectMocks
    private AlertingService alertingService;

    // ── evaluateAlertRules ──────────────────────────────────────────────

    @Test
    void evaluateAlertRules_triggersAlertWhenConditionMet() {
        UUID apiId = UUID.randomUUID();
        AlertRuleEntity rule = AlertRuleEntity.builder()
                .id(UUID.randomUUID())
                .name("High Error Rate")
                .metric("error_rate")
                .condition("GT")
                .threshold(BigDecimal.valueOf(5))
                .windowMinutes(10)
                .apiId(apiId)
                .enabled(true)
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(requestLogStore.queryMetric("error_rate", 10, apiId)).thenReturn(BigDecimal.valueOf(10));

        alertingService.evaluateAlertRules();

        ArgumentCaptor<AlertHistoryEntity> captor = ArgumentCaptor.forClass(AlertHistoryEntity.class);
        verify(alertHistoryRepository).save(captor.capture());
        AlertHistoryEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("TRIGGERED");
        assertThat(saved.getValue()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(saved.getRule()).isEqualTo(rule);
    }

    @Test
    void evaluateAlertRules_doesNotTriggerWhenConditionNotMet() {
        AlertRuleEntity rule = AlertRuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Low Latency")
                .metric("avg_latency")
                .condition("GT")
                .threshold(BigDecimal.valueOf(100))
                .windowMinutes(5)
                .enabled(true)
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(requestLogStore.queryMetric("avg_latency", 5, null)).thenReturn(BigDecimal.valueOf(50));

        alertingService.evaluateAlertRules();

        verify(alertHistoryRepository, never()).save(any());
    }

    @Test
    void evaluateAlertRules_doesNotTriggerWhenNoData() {
        AlertRuleEntity rule = AlertRuleEntity.builder()
                .id(UUID.randomUUID())
                .name("No Data Rule")
                .metric("some_metric")
                .condition("GT")
                .threshold(BigDecimal.ONE)
                .windowMinutes(5)
                .enabled(true)
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(requestLogStore.queryMetric("some_metric", 5, null)).thenReturn(null);

        alertingService.evaluateAlertRules();

        verify(alertHistoryRepository, never()).save(any());
    }

    @Test
    void evaluateAlertRules_continuesAfterExceptionInOneRule() {
        AlertRuleEntity rule1 = AlertRuleEntity.builder()
                .id(UUID.randomUUID()).name("r1").metric("m").condition("GT")
                .threshold(BigDecimal.ONE).windowMinutes(1).enabled(true).build();
        AlertRuleEntity rule2 = AlertRuleEntity.builder()
                .id(UUID.randomUUID()).name("r2").metric("m2").condition("GT")
                .threshold(BigDecimal.ONE).windowMinutes(1).enabled(true).build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule1, rule2));
        when(requestLogStore.queryMetric("m", 1, null))
                .thenThrow(new RuntimeException("DB error"));
        when(requestLogStore.queryMetric("m2", 1, null)).thenReturn(BigDecimal.TEN);

        alertingService.evaluateAlertRules();

        // rule2 should still be evaluated and trigger
        verify(alertHistoryRepository).save(any());
    }

    // ── evaluateCondition (tested indirectly via evaluateAlertRules) ─────

    @ParameterizedTest
    @CsvSource({
            "GT, 10, 5, true",
            "GT, 5, 10, false",
            "GT, 5, 5, false",
            "LT, 3, 5, true",
            "LT, 5, 3, false",
            "EQ, 5, 5, true",
            "EQ, 5, 6, false",
            "GTE, 5, 5, true",
            "GTE, 6, 5, true",
            "GTE, 4, 5, false",
            "LTE, 5, 5, true",
            "LTE, 4, 5, true",
            "LTE, 6, 5, false",
            "GREATER_THAN, 10, 5, true",
            "LESS_THAN, 3, 5, true",
            "EQUALS, 7, 7, true"
    })
    void evaluateCondition_allOperators(String condition, int currentValue, int threshold, boolean shouldTrigger) {
        AlertRuleEntity rule = AlertRuleEntity.builder()
                .id(UUID.randomUUID())
                .name("test-rule")
                .metric("test_metric")
                .condition(condition)
                .threshold(BigDecimal.valueOf(threshold))
                .windowMinutes(5)
                .enabled(true)
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(requestLogStore.queryMetric("test_metric", 5, null))
                .thenReturn(BigDecimal.valueOf(currentValue));

        alertingService.evaluateAlertRules();

        if (shouldTrigger) {
            verify(alertHistoryRepository).save(any(AlertHistoryEntity.class));
        } else {
            verify(alertHistoryRepository, never()).save(any());
        }
    }

    @Test
    void evaluateCondition_unknownCondition_doesNotTrigger() {
        AlertRuleEntity rule = AlertRuleEntity.builder()
                .id(UUID.randomUUID()).name("bad-cond").metric("m").condition("INVALID")
                .threshold(BigDecimal.ONE).windowMinutes(1).enabled(true).build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(requestLogStore.queryMetric("m", 1, null)).thenReturn(BigDecimal.TEN);

        alertingService.evaluateAlertRules();

        verify(alertHistoryRepository, never()).save(any());
    }

    // ── createRule ───────────────────────────────────────────────────────

    @Test
    void createRule_savesEntityAndReturnsResponse() {
        UUID apiId = UUID.randomUUID();
        AlertRuleRequest request = AlertRuleRequest.builder()
                .name("High Error Rate")
                .metric("error_rate")
                .condition("GT")
                .threshold(BigDecimal.valueOf(5))
                .windowMinutes(10)
                .apiId(apiId)
                .enabled(true)
                .channels("[\"email\"]")
                .build();

        UUID ruleId = UUID.randomUUID();
        Instant now = Instant.now();
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRuleEntity e = inv.getArgument(0);
            e.setId(ruleId);
            e.setCreatedAt(now);
            return e;
        });

        AlertRuleResponse response = alertingService.createRule(request);

        assertThat(response.getId()).isEqualTo(ruleId);
        assertThat(response.getName()).isEqualTo("High Error Rate");
        assertThat(response.getMetric()).isEqualTo("error_rate");
        assertThat(response.getCondition()).isEqualTo("GT");
        assertThat(response.getThreshold()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.getApiId()).isEqualTo(apiId);
        assertThat(response.isEnabled()).isTrue();
    }

    // ── listRules ───────────────────────────────────────────────────────

    @Test
    void listRules_returnsAllRules() {
        AlertRuleEntity e1 = AlertRuleEntity.builder().id(UUID.randomUUID()).name("r1").build();
        AlertRuleEntity e2 = AlertRuleEntity.builder().id(UUID.randomUUID()).name("r2").build();
        when(alertRuleRepository.findAll()).thenReturn(List.of(e1, e2));

        List<AlertRuleResponse> result = alertingService.listRules();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("r1");
        assertThat(result.get(1).getName()).isEqualTo("r2");
    }

    @Test
    void listRules_returnsEmptyListWhenNoRules() {
        when(alertRuleRepository.findAll()).thenReturn(List.of());

        List<AlertRuleResponse> result = alertingService.listRules();

        assertThat(result).isEmpty();
    }

    // ── updateRule ──────────────────────────────────────────────────────

    @Test
    void updateRule_updatesAllFieldsAndReturnsResponse() {
        UUID ruleId = UUID.randomUUID();
        AlertRuleEntity existing = AlertRuleEntity.builder()
                .id(ruleId).name("old").metric("m").condition("GT")
                .threshold(BigDecimal.ONE).windowMinutes(1).enabled(true).build();

        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleRequest request = AlertRuleRequest.builder()
                .name("new").metric("new_m").condition("LT")
                .threshold(BigDecimal.TEN).windowMinutes(15).enabled(false).build();

        AlertRuleResponse result = alertingService.updateRule(ruleId, request);

        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getMetric()).isEqualTo("new_m");
        assertThat(result.getCondition()).isEqualTo("LT");
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void updateRule_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertingService.updateRule(id, new AlertRuleRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── deleteRule ──────────────────────────────────────────────────────

    @Test
    void deleteRule_deletesExistingRule() {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.existsById(id)).thenReturn(true);

        alertingService.deleteRule(id);

        verify(alertRuleRepository).deleteById(id);
    }

    @Test
    void deleteRule_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> alertingService.deleteRule(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── getAlertHistory ─────────────────────────────────────────────────

    @Test
    void getAlertHistory_withStatus_filtersResults() {
        AlertRuleEntity rule = AlertRuleEntity.builder().id(UUID.randomUUID()).name("r").build();
        AlertHistoryEntity entity = AlertHistoryEntity.builder()
                .id(1L).rule(rule).status("TRIGGERED").value(BigDecimal.TEN).build();
        Page<AlertHistoryEntity> page = new PageImpl<>(List.of(entity));

        when(alertHistoryRepository.findByStatusOrderByTriggeredAtDesc(eq("TRIGGERED"), any(Pageable.class)))
                .thenReturn(page);

        Page<AlertHistoryResponse> result = alertingService.getAlertHistory(0, 20, "triggered");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("TRIGGERED");
    }

    @Test
    void getAlertHistory_withoutStatus_returnsAll() {
        Page<AlertHistoryEntity> page = new PageImpl<>(List.of());
        when(alertHistoryRepository.findAllByOrderByTriggeredAtDesc(any(Pageable.class))).thenReturn(page);

        Page<AlertHistoryResponse> result = alertingService.getAlertHistory(0, 20, null);

        assertThat(result.getContent()).isEmpty();
        verify(alertHistoryRepository).findAllByOrderByTriggeredAtDesc(any());
    }

    @Test
    void getAlertHistory_blankStatus_treatedAsNoFilter() {
        Page<AlertHistoryEntity> page = new PageImpl<>(List.of());
        when(alertHistoryRepository.findAllByOrderByTriggeredAtDesc(any(Pageable.class))).thenReturn(page);

        alertingService.getAlertHistory(0, 10, "   ");

        verify(alertHistoryRepository).findAllByOrderByTriggeredAtDesc(any());
    }

    // ── acknowledgeAlert ────────────────────────────────────────────────

    @Test
    void acknowledgeAlert_setsStatusAndTimestamp() {
        UUID ackBy = UUID.randomUUID();
        AlertRuleEntity rule = AlertRuleEntity.builder().id(UUID.randomUUID()).name("r").build();
        AlertHistoryEntity entity = AlertHistoryEntity.builder()
                .id(1L).rule(rule).status("TRIGGERED").value(BigDecimal.ONE).build();

        when(alertHistoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(alertHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertHistoryResponse result = alertingService.acknowledgeAlert(1L, ackBy);

        assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(result.getAcknowledgedBy()).isEqualTo(ackBy);
        assertThat(result.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void acknowledgeAlert_throwsWhenNotFound() {
        when(alertHistoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertingService.acknowledgeAlert(99L, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
