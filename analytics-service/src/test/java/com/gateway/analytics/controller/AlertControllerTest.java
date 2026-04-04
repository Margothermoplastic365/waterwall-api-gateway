package com.gateway.analytics.controller;

import com.gateway.analytics.dto.AlertHistoryResponse;
import com.gateway.analytics.dto.AlertRuleRequest;
import com.gateway.analytics.dto.AlertRuleResponse;
import com.gateway.analytics.service.AlertingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertingService alertingService;

    @InjectMocks
    private AlertController alertController;

    @Test
    void createRule_returnsCreatedWithResponse() {
        AlertRuleRequest request = AlertRuleRequest.builder()
                .name("High Latency").metric("avg_latency").condition("GT")
                .threshold(BigDecimal.valueOf(100)).windowMinutes(10).enabled(true).build();
        AlertRuleResponse response = AlertRuleResponse.builder()
                .id(UUID.randomUUID()).name("High Latency").build();

        when(alertingService.createRule(request)).thenReturn(response);

        ResponseEntity<AlertRuleResponse> result = alertController.createRule(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getName()).isEqualTo("High Latency");
    }

    @Test
    void listRules_returnsOkWithList() {
        AlertRuleResponse r1 = AlertRuleResponse.builder().id(UUID.randomUUID()).name("r1").build();
        AlertRuleResponse r2 = AlertRuleResponse.builder().id(UUID.randomUUID()).name("r2").build();
        when(alertingService.listRules()).thenReturn(List.of(r1, r2));

        ResponseEntity<List<AlertRuleResponse>> result = alertController.listRules();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
    }

    @Test
    void updateRule_returnsOkWithUpdatedResponse() {
        UUID id = UUID.randomUUID();
        AlertRuleRequest request = AlertRuleRequest.builder().name("Updated").build();
        AlertRuleResponse response = AlertRuleResponse.builder().id(id).name("Updated").build();
        when(alertingService.updateRule(id, request)).thenReturn(response);

        ResponseEntity<AlertRuleResponse> result = alertController.updateRule(id, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteRule_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> result = alertController.deleteRule(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(alertingService).deleteRule(id);
    }

    @Test
    void getAlertHistory_returnsOkWithPage() {
        AlertHistoryResponse entry = AlertHistoryResponse.builder()
                .id(1L).status("TRIGGERED").build();
        Page<AlertHistoryResponse> page = new PageImpl<>(List.of(entry));
        when(alertingService.getAlertHistory(0, 20, "TRIGGERED")).thenReturn(page);

        ResponseEntity<Page<AlertHistoryResponse>> result =
                alertController.getAlertHistory(0, 20, "TRIGGERED");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent()).hasSize(1);
    }

    @Test
    void getAlertHistory_withNullStatus_delegatesCorrectly() {
        Page<AlertHistoryResponse> page = new PageImpl<>(List.of());
        when(alertingService.getAlertHistory(0, 20, null)).thenReturn(page);

        ResponseEntity<Page<AlertHistoryResponse>> result =
                alertController.getAlertHistory(0, 20, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void acknowledgeAlert_returnsOkWithAcknowledgedAlert() {
        UUID ackBy = UUID.randomUUID();
        AlertHistoryResponse response = AlertHistoryResponse.builder()
                .id(1L).status("ACKNOWLEDGED").acknowledgedBy(ackBy).build();
        when(alertingService.acknowledgeAlert(1L, ackBy)).thenReturn(response);

        ResponseEntity<AlertHistoryResponse> result =
                alertController.acknowledgeAlert(1L, ackBy);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getStatus()).isEqualTo("ACKNOWLEDGED");
    }
}
