package com.gateway.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldCreateMinimalResponseUsingFactory() {
        Instant before = Instant.now();

        ApiErrorResponse response = ApiErrorResponse.of(400, "VAL_001", "Validation failed");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorCode()).isEqualTo("VAL_001");
        assertThat(response.getMessage()).isEqualTo("Validation failed");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldBuildWithAllFields() {
        Instant now = Instant.now();
        List<ApiErrorResponse.FieldError> details = List.of(
                ApiErrorResponse.FieldError.builder()
                        .field("email")
                        .message("must be valid")
                        .rejectedValue("not-an-email")
                        .build()
        );

        ApiErrorResponse response = ApiErrorResponse.builder()
                .status(400)
                .error("VALIDATION_ERROR")
                .errorCode("VAL_001")
                .message("Request validation failed")
                .details(details)
                .traceId("abc-123")
                .timestamp(now)
                .path("/v1/users")
                .build();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getErrorCode()).isEqualTo("VAL_001");
        assertThat(response.getMessage()).isEqualTo("Request validation failed");
        assertThat(response.getDetails()).hasSize(1);
        assertThat(response.getTraceId()).isEqualTo("abc-123");
        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getPath()).isEqualTo("/v1/users");
    }

    @Test
    void shouldCreateFieldError() {
        ApiErrorResponse.FieldError fieldError = ApiErrorResponse.FieldError.builder()
                .field("age")
                .message("must be positive")
                .rejectedValue(-1)
                .build();

        assertThat(fieldError.getField()).isEqualTo("age");
        assertThat(fieldError.getMessage()).isEqualTo("must be positive");
        assertThat(fieldError.getRejectedValue()).isEqualTo(-1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSerializeToJsonWithSnakeCaseProperties() throws Exception {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .status(401)
                .error("AUTHENTICATION_ERROR")
                .errorCode("AUTH_001")
                .message("Authentication failed")
                .traceId("trace-xyz-789")
                .timestamp(Instant.parse("2026-03-25T14:30:00Z"))
                .path("/v1/protected")
                .build();

        String json = objectMapper.writeValueAsString(response);
        Map<String, Object> map = objectMapper.readValue(json, Map.class);

        assertThat(map).containsKey("error_code");
        assertThat(map.get("error_code")).isEqualTo("AUTH_001");
        assertThat(map).containsKey("trace_id");
        assertThat(map.get("trace_id")).isEqualTo("trace-xyz-789");
        // Verify camelCase versions are NOT present
        assertThat(map).doesNotContainKey("errorCode");
        assertThat(map).doesNotContainKey("traceId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeNullFieldsFromJson() throws Exception {
        ApiErrorResponse response = ApiErrorResponse.of(500, "SYS_001", "Internal error");
        // details, traceId, path, error are all null

        String json = objectMapper.writeValueAsString(response);
        Map<String, Object> map = objectMapper.readValue(json, Map.class);

        assertThat(map).doesNotContainKey("details");
        assertThat(map).doesNotContainKey("trace_id");
        assertThat(map).doesNotContainKey("path");
        assertThat(map).doesNotContainKey("error");
        // Non-null fields should be present
        assertThat(map).containsKey("status");
        assertThat(map).containsKey("error_code");
        assertThat(map).containsKey("message");
        assertThat(map).containsKey("timestamp");
    }
}
