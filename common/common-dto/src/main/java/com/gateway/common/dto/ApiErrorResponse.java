package com.gateway.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope returned by ALL services.
 * <p>
 * Matches the format specified in Section 10.2 of the API Gateway Requirements:
 * <pre>
 * {
 *   "status": 400,
 *   "error": "VALIDATION_ERROR",
 *   "error_code": "VAL_001",
 *   "message": "Request validation failed",
 *   "details": [ { "field": "email", "message": "...", "rejected_value": "..." } ],
 *   "trace_id": "abc-123-def-456",
 *   "timestamp": "2026-03-25T14:30:00Z",
 *   "path": "/v1/users"
 * }
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private int status;

    private String error;

    @JsonProperty("error_code")
    private String errorCode;

    private String message;

    private List<FieldError> details;

    @JsonProperty("trace_id")
    private String traceId;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String path;

    /**
     * Represents a single field-level validation error.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {

        private String field;

        private String message;

        @JsonProperty("rejected_value")
        private Object rejectedValue;
    }

    /**
     * Convenience factory for creating a minimal error response.
     *
     * @param status    HTTP status code
     * @param errorCode application error code (e.g. VAL_001, AUTH_001)
     * @param message   human-readable error message
     * @return a pre-populated {@link ApiErrorResponse}
     */
    public static ApiErrorResponse of(int status, String errorCode, String message) {
        return ApiErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
