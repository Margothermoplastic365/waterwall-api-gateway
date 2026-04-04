package com.gateway.analytics.controller;

import com.gateway.analytics.dto.ApiComparisonEntry;
import com.gateway.analytics.dto.ApiMetricsResponse;
import com.gateway.analytics.dto.DashboardResponse;
import com.gateway.analytics.dto.TopApiEntry;
import com.gateway.analytics.dto.TopConsumerEntry;
import com.gateway.analytics.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void getDashboard_returnsOkWithDashboardResponse() {
        DashboardResponse response = DashboardResponse.builder()
                .totalRequests(500).avgLatencyMs(20.0).errorRate(1.0).build();
        when(dashboardService.getDashboard("24h")).thenReturn(response);

        ResponseEntity<DashboardResponse> result = dashboardController.getDashboard("24h");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalRequests()).isEqualTo(500);
    }

    @Test
    void getApiMetrics_returnsOkWithMetrics() {
        UUID apiId = UUID.randomUUID();
        ApiMetricsResponse response = ApiMetricsResponse.builder()
                .apiId(apiId).totalRequests(100).build();
        when(dashboardService.getApiMetrics(apiId, "7d")).thenReturn(response);

        ResponseEntity<ApiMetricsResponse> result = dashboardController.getApiMetrics(apiId, "7d");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getApiId()).isEqualTo(apiId);
    }

    @Test
    void getTopApis_returnsOkWithList() {
        TopApiEntry entry = TopApiEntry.builder().requestCount(50).build();
        when(dashboardService.getTopApis(10, "requests", "24h")).thenReturn(List.of(entry));

        ResponseEntity<List<TopApiEntry>> result = dashboardController.getTopApis(10, "requests", "24h");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getTopConsumers_returnsOkWithList() {
        TopConsumerEntry entry = TopConsumerEntry.builder().requestCount(30).build();
        when(dashboardService.getTopConsumers(10, "24h")).thenReturn(List.of(entry));

        ResponseEntity<List<TopConsumerEntry>> result = dashboardController.getTopConsumers(10, "24h");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void exportReport_returnsOkWithCsvContent() {
        String csv = "col1,col2\nval1,val2\n";
        when(dashboardService.exportCsv("24h")).thenReturn(csv);

        ResponseEntity<String> result = dashboardController.exportReport("csv", "24h");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(csv);
        assertThat(result.getHeaders().getFirst("Content-Disposition"))
                .contains("dashboard-report.csv");
        assertThat(result.getHeaders().getContentType().toString()).contains("text/csv");
    }

    @Test
    void compareApis_returnsOkWithComparisonList() {
        UUID id1 = UUID.randomUUID();
        List<UUID> ids = List.of(id1);
        ApiComparisonEntry entry = ApiComparisonEntry.builder().apiId(id1).totalRequests(80).build();
        when(dashboardService.compareApis(ids, "24h")).thenReturn(List.of(entry));

        ResponseEntity<List<ApiComparisonEntry>> result = dashboardController.compareApis(ids, "24h");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getApiId()).isEqualTo(id1);
    }
}
