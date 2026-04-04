package com.gateway.analytics.service;

import com.gateway.analytics.dto.ApiComparisonEntry;
import com.gateway.analytics.dto.ApiMetricsResponse;
import com.gateway.analytics.dto.DashboardResponse;
import com.gateway.analytics.dto.TopApiEntry;
import com.gateway.analytics.dto.TopConsumerEntry;
import com.gateway.analytics.store.RequestLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private RequestLogStore store;

    @InjectMocks
    private DashboardService dashboardService;

    // ── getDashboard ────────────────────────────────────────────────────

    @Test
    void getDashboard_returnsAggregatedStats() {
        DashboardResponse stats = DashboardResponse.builder()
                .totalRequests(1000)
                .avgLatencyMs(42.5)
                .avgLatency(42.5)
                .errorRate(2.3)
                .activeApis(5)
                .build();
        Map<String, Long> breakdown = Map.of("200", 900L, "500", 100L);

        when(store.getDashboardStats("24 hours")).thenReturn(stats);
        when(store.getStatusCodeBreakdown("24 hours")).thenReturn(breakdown);

        DashboardResponse result = dashboardService.getDashboard("24h");

        assertThat(result.getTotalRequests()).isEqualTo(1000);
        assertThat(result.getAvgLatencyMs()).isEqualTo(42.5);
        assertThat(result.getErrorRate()).isEqualTo(2.3);
        assertThat(result.getActiveApis()).isEqualTo(5);
        assertThat(result.getStatusCodeBreakdown()).containsEntry("200", 900L);
    }

    @Test
    void getDashboard_nullTimeRange_defaultsTo24Hours() {
        DashboardResponse stats = DashboardResponse.builder().build();
        when(store.getDashboardStats("24 hours")).thenReturn(stats);
        when(store.getStatusCodeBreakdown("24 hours")).thenReturn(Map.of());

        dashboardService.getDashboard(null);

        verify(store).getDashboardStats("24 hours");
    }

    // ── getApiMetrics ───────────────────────────────────────────────────

    @Test
    void getApiMetrics_delegatesToStoreWithConvertedInterval() {
        UUID apiId = UUID.randomUUID();
        ApiMetricsResponse expected = ApiMetricsResponse.builder()
                .apiId(apiId)
                .totalRequests(500)
                .build();
        when(store.getApiMetrics(apiId, "7 days")).thenReturn(expected);

        ApiMetricsResponse result = dashboardService.getApiMetrics(apiId, "7d");

        assertThat(result.getApiId()).isEqualTo(apiId);
        assertThat(result.getTotalRequests()).isEqualTo(500);
    }

    // ── getTopApis ──────────────────────────────────────────────────────

    @Test
    void getTopApis_returnsListFromStore() {
        TopApiEntry entry = TopApiEntry.builder()
                .apiId(UUID.randomUUID())
                .requestCount(100)
                .build();
        when(store.getTopApis(10, "requests", "1 hour")).thenReturn(List.of(entry));

        List<TopApiEntry> result = dashboardService.getTopApis(10, "requests", "1h");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestCount()).isEqualTo(100);
    }

    // ── getTopConsumers ─────────────────────────────────────────────────

    @Test
    void getTopConsumers_returnsListFromStore() {
        TopConsumerEntry entry = TopConsumerEntry.builder()
                .consumerId(UUID.randomUUID())
                .requestCount(250)
                .build();
        when(store.getTopConsumers(5, "6 hours")).thenReturn(List.of(entry));

        List<TopConsumerEntry> result = dashboardService.getTopConsumers(5, "6h");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestCount()).isEqualTo(250);
    }

    // ── exportCsv ───────────────────────────────────────────────────────

    @Test
    void exportCsv_containsSummaryAndTopApis() {
        DashboardResponse stats = DashboardResponse.builder()
                .totalRequests(200)
                .avgLatencyMs(30.0)
                .errorRate(1.5)
                .build();
        TopApiEntry topApi = TopApiEntry.builder()
                .apiId(UUID.randomUUID())
                .requestCount(100)
                .errorCount(5)
                .avgLatencyMs(25.0)
                .errorRate(5.0)
                .build();

        when(store.getDashboardStats("24 hours")).thenReturn(stats);
        when(store.getStatusCodeBreakdown("24 hours")).thenReturn(Map.of());
        when(store.getTopApis(50, "requests", "24 hours")).thenReturn(List.of(topApi));

        String csv = dashboardService.exportCsv("24h");

        assertThat(csv).contains("# Platform Dashboard Export");
        assertThat(csv).contains("# Time Range: 24h");
        assertThat(csv).contains("total_requests,avg_latency_ms,error_rate");
        assertThat(csv).contains("200,30.0,1.5");
        assertThat(csv).contains("# Top APIs");
        assertThat(csv).contains("api_id,request_count,error_count,avg_latency_ms,error_rate");
    }

    // ── getPercentiles ──────────────────────────────────────────────────

    @Test
    void getPercentiles_delegatesToStore() {
        Map<String, Double> percentiles = Map.of("p50", 10.0, "p95", 80.0, "p99", 150.0);
        when(store.getPercentiles("30 days")).thenReturn(percentiles);

        Map<String, Double> result = dashboardService.getPercentiles("30d");

        assertThat(result).containsEntry("p50", 10.0);
        assertThat(result).containsEntry("p95", 80.0);
        assertThat(result).containsEntry("p99", 150.0);
    }

    // ── compareApis ─────────────────────────────────────────────────────

    @Test
    void compareApis_returnsEmptyListWhenApiIdsNull() {
        List<ApiComparisonEntry> result = dashboardService.compareApis(null, "24h");
        assertThat(result).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    void compareApis_returnsEmptyListWhenApiIdsEmpty() {
        List<ApiComparisonEntry> result = dashboardService.compareApis(List.of(), "24h");
        assertThat(result).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    void compareApis_delegatesToStoreWithConvertedInterval() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2);
        ApiComparisonEntry entry = ApiComparisonEntry.builder().apiId(id1).totalRequests(50).build();
        when(store.compareApis(ids, "24 hours")).thenReturn(List.of(entry));

        List<ApiComparisonEntry> result = dashboardService.compareApis(ids, "24h");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApiId()).isEqualTo(id1);
    }

    // ── toInterval mapping ──────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "1h, 1 hour",
            "6h, 6 hours",
            "24h, 24 hours",
            "7d, 7 days",
            "30d, 30 days",
            "unknown, 24 hours"
    })
    void toInterval_mapsCorrectly(String input, String expectedInterval) {
        // We verify the interval mapping indirectly via getPercentiles
        when(store.getPercentiles(expectedInterval)).thenReturn(Map.of());
        dashboardService.getPercentiles(input);
        verify(store).getPercentiles(expectedInterval);
    }
}
