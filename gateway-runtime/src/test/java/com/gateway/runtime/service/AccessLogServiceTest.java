package com.gateway.runtime.service;

import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessLogServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<BaseEvent> eventCaptor;

    private AccessLogService accessLogService;

    @BeforeEach
    void setUp() {
        accessLogService = new AccessLogService(eventPublisher);
    }

    @Test
    void shouldPublishAccessLogEvent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

        accessLogService.logRequest(request, 200, 150L, 120L, 30L, 512L, 1024L,
                "trace-abc", "span-xyz");

        verify(eventPublisher).publish(
                eq(RabbitMQExchanges.ANALYTICS_INGEST),
                eq("request.logged"),
                any(AccessLogService.AccessLogEvent.class)
        );
    }

    @Test
    void shouldPopulateEventFields() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "TestClient/1.0");

        UUID apiId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        GatewayRoute route = GatewayRoute.builder()
                .apiId(apiId)
                .routeId(routeId)
                .apiName("Orders API")
                .authTypes(List.of("JWT"))
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();
        request.setAttribute("gateway.matchedRoute", matchedRoute);

        accessLogService.logRequest(request, 201, 250L, 200L, 50L, 1024L, 2048L,
                "trace-123", "span-456");

        verify(eventPublisher).publish(
                eq(RabbitMQExchanges.ANALYTICS_INGEST),
                eq("request.logged"),
                eventCaptor.capture()
        );

        AccessLogService.AccessLogEvent event =
                (AccessLogService.AccessLogEvent) eventCaptor.getValue();

        assertThat(event.getApiId()).isEqualTo(apiId.toString());
        assertThat(event.getRouteId()).isEqualTo(routeId.toString());
        assertThat(event.getApiName()).isEqualTo("Orders API");
        assertThat(event.getMethod()).isEqualTo("POST");
        assertThat(event.getPath()).isEqualTo("/api/orders");
        assertThat(event.getStatusCode()).isEqualTo(201);
        assertThat(event.getLatencyMs()).isEqualTo(250L);
        assertThat(event.getUpstreamLatencyMs()).isEqualTo(200L);
        assertThat(event.getGatewayLatencyMs()).isEqualTo(50L);
        assertThat(event.getRequestSize()).isEqualTo(1024L);
        assertThat(event.getResponseSize()).isEqualTo(2048L);
        assertThat(event.getTraceId()).isEqualTo("trace-123");
        assertThat(event.getSpanId()).isEqualTo("span-456");
        assertThat(event.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("TestClient/1.0");
        assertThat(event.getAuthType()).isEqualTo("JWT");
    }

    @Test
    void shouldUseXForwardedForForClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");

        accessLogService.logRequest(request, 200, 100L, 80L, 20L, 0L, 256L,
                "trace-xff", "span-xff");

        verify(eventPublisher).publish(
                eq(RabbitMQExchanges.ANALYTICS_INGEST),
                eq("request.logged"),
                eventCaptor.capture()
        );

        AccessLogService.AccessLogEvent event =
                (AccessLogService.AccessLogEvent) eventCaptor.getValue();
        assertThat(event.getClientIp()).isEqualTo("203.0.113.50");
    }
}
