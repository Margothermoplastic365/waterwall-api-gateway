package com.gateway.analytics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.analytics.entity.RequestLogEntity;
import com.gateway.analytics.repository.RequestLogRepository;
import com.gateway.analytics.service.RequestLogConsumer;
import com.gateway.analytics.store.RequestLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestLogConsumerTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    @Mock
    private RequestLogStore requestLogStore;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RequestLogConsumer requestLogConsumer;

    private Message createMessage(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    // ── handleRequestLogged ─────────────────────────────────────────────

    @Test
    void handleRequestLogged_parsesAllFieldsCorrectly() {
        UUID apiId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        String json = String.format("""
                {
                    "traceId": "trace-123",
                    "apiId": "%s",
                    "routeId": "%s",
                    "consumerId": "%s",
                    "applicationId": "%s",
                    "apiName": "PetStore",
                    "consumerEmail": "user@example.com",
                    "method": "GET",
                    "path": "/api/pets",
                    "statusCode": 200,
                    "latencyMs": 45,
                    "requestSize": 128,
                    "responseSize": 1024,
                    "authType": "API_KEY",
                    "clientIp": "10.0.0.1",
                    "userAgent": "curl/7.68.0",
                    "errorCode": null,
                    "gatewayNode": "node-1",
                    "mockMode": false
                }
                """, apiId, routeId, consumerId, applicationId);

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        RequestLogEntity entity = captor.getValue();

        assertThat(entity.getTraceId()).isEqualTo("trace-123");
        assertThat(entity.getApiId()).isEqualTo(apiId);
        assertThat(entity.getRouteId()).isEqualTo(routeId);
        assertThat(entity.getConsumerId()).isEqualTo(consumerId);
        assertThat(entity.getApplicationId()).isEqualTo(applicationId);
        assertThat(entity.getApiName()).isEqualTo("PetStore");
        assertThat(entity.getConsumerEmail()).isEqualTo("user@example.com");
        assertThat(entity.getMethod()).isEqualTo("GET");
        assertThat(entity.getPath()).isEqualTo("/api/pets");
        assertThat(entity.getStatusCode()).isEqualTo(200);
        assertThat(entity.getLatencyMs()).isEqualTo(45);
        assertThat(entity.getRequestSize()).isEqualTo(128);
        assertThat(entity.getResponseSize()).isEqualTo(1024);
        assertThat(entity.getAuthType()).isEqualTo("API_KEY");
        assertThat(entity.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(entity.getUserAgent()).isEqualTo("curl/7.68.0");
        assertThat(entity.getErrorCode()).isNull();
        assertThat(entity.getGatewayNode()).isEqualTo("node-1");
        assertThat(entity.isMockMode()).isFalse();
    }

    @Test
    void handleRequestLogged_nullAndMissingFields_handledGracefully() {
        String json = """
                {
                    "method": "POST",
                    "path": "/api/submit",
                    "statusCode": 201,
                    "latencyMs": 100
                }
                """;

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        RequestLogEntity entity = captor.getValue();

        assertThat(entity.getTraceId()).isNull();
        assertThat(entity.getApiId()).isNull();
        assertThat(entity.getRouteId()).isNull();
        assertThat(entity.getConsumerId()).isNull();
        assertThat(entity.getMethod()).isEqualTo("POST");
        assertThat(entity.getPath()).isEqualTo("/api/submit");
        assertThat(entity.getStatusCode()).isEqualTo(201);
        assertThat(entity.isMockMode()).isFalse();
    }

    @Test
    void handleRequestLogged_invalidUuid_treatedAsNull() {
        String json = """
                {
                    "apiId": "not-a-uuid",
                    "method": "GET",
                    "path": "/test",
                    "statusCode": 200,
                    "latencyMs": 10
                }
                """;

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        assertThat(captor.getValue().getApiId()).isNull();
    }

    @Test
    void handleRequestLogged_invalidJson_doesNotThrow() {
        String invalidJson = "this is not json at all";

        requestLogConsumer.handleRequestLogged(createMessage(invalidJson));

        verify(requestLogStore, never()).save(any());
    }

    @Test
    void handleRequestLogged_mockModeTrue_parsedCorrectly() {
        String json = """
                {
                    "method": "GET",
                    "path": "/mock",
                    "statusCode": 200,
                    "latencyMs": 5,
                    "mockMode": true
                }
                """;

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        assertThat(captor.getValue().isMockMode()).isTrue();
    }

    @Test
    void handleRequestLogged_blankStringFields_treatedAsNull() {
        String json = """
                {
                    "traceId": "   ",
                    "method": "GET",
                    "path": "/test",
                    "statusCode": 200,
                    "latencyMs": 10
                }
                """;

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        assertThat(captor.getValue().getTraceId()).isNull();
    }

    @Test
    void handleRequestLogged_storeThrows_exceptionCaught() {
        String json = """
                {
                    "method": "GET",
                    "path": "/test",
                    "statusCode": 200,
                    "latencyMs": 10
                }
                """;

        doThrow(new RuntimeException("DB down")).when(requestLogStore).save(any());

        // Should not propagate exception
        requestLogConsumer.handleRequestLogged(createMessage(json));

        verify(requestLogStore).save(any());
    }

    @Test
    void handleRequestLogged_defaultsNumericFieldsToZero_whenMissing() {
        String json = """
                {
                    "method": "GET",
                    "path": "/test"
                }
                """;

        requestLogConsumer.handleRequestLogged(createMessage(json));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogStore).save(captor.capture());
        RequestLogEntity entity = captor.getValue();
        assertThat(entity.getStatusCode()).isZero();
        assertThat(entity.getLatencyMs()).isZero();
        assertThat(entity.getRequestSize()).isZero();
        assertThat(entity.getResponseSize()).isZero();
    }
}
