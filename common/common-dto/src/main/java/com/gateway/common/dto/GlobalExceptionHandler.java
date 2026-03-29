package com.gateway.common.dto;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Global exception handler that catches all exceptions and returns a
 * consistent {@link ApiErrorResponse} envelope.
 * <p>
 * Implements Section 10.2 of the API Gateway Requirements:
 * <ul>
 *   <li>Stack traces are NEVER exposed to clients (logged internally at ERROR level)</li>
 *   <li>Correlation ID (trace_id) is propagated from MDC</li>
 *   <li>Every response uses the standard error envelope format</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TRACE_ID_KEY = "traceId";

    // ---------------------------------------------------------------------------
    // 400 — Validation errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ApiErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();

        ApiErrorResponse body = buildResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "VAL_001",
                "Request validation failed",
                request.getRequestURI()
        );
        body.setDetails(fieldErrors);

        log.warn("Validation failed on {}: {} field error(s)", request.getRequestURI(), fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(cv -> ApiErrorResponse.FieldError.builder()
                        .field(extractFieldName(cv))
                        .message(cv.getMessage())
                        .rejectedValue(cv.getInvalidValue())
                        .build())
                .toList();

        ApiErrorResponse body = buildResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "VAL_002",
                "Constraint violation",
                request.getRequestURI()
        );
        body.setDetails(fieldErrors);

        log.warn("Constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ---------------------------------------------------------------------------
    // 401 — Authentication errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "AUTHENTICATION_ERROR",
                "AUTH_001",
                "Authentication failed",
                request.getRequestURI()
        );

        log.warn("Authentication failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // ---------------------------------------------------------------------------
    // 403 — Authorization errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.FORBIDDEN.value(),
                "AUTHORIZATION_ERROR",
                "AUTHZ_001",
                "Access denied",
                request.getRequestURI()
        );

        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ---------------------------------------------------------------------------
    // 404 — Not found errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "NOT_001",
                ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                request.getRequestURI()
        );

        log.warn("Entity not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNoSuchElement(
            NoSuchElementException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "NOT_001",
                ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                request.getRequestURI()
        );

        log.warn("Element not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ---------------------------------------------------------------------------
    // 409 — Conflict errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                "CONFLICT_001",
                "Data conflict — the resource already exists or a constraint was violated",
                request.getRequestURI()
        );

        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ---------------------------------------------------------------------------
    // 429 — Rate limit errors
    // ---------------------------------------------------------------------------

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "RATE_001",
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("Rate limit exceeded on {} for key '{}': limit={}, remaining={}, resetAt={}",
                request.getRequestURI(), ex.getKey(), ex.getLimit(), ex.getRemaining(), ex.getResetAt());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getResetAt().getEpochSecond() - Instant.now().getEpochSecond()))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(ex.getRemaining()))
                .header("X-RateLimit-Reset", String.valueOf(ex.getResetAt().getEpochSecond()))
                .body(body);
    }

    // ---------------------------------------------------------------------------
    // 500 — Fallback for all unhandled exceptions
    // ---------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllUncaught(
            Exception ex, HttpServletRequest request) {

        ApiErrorResponse body = buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "SYS_001",
                "An unexpected error occurred",
                request.getRequestURI()
        );

        log.error("Unhandled exception on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ApiErrorResponse buildResponse(int status, String error, String errorCode,
                                           String message, String path) {
        return ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .traceId(MDC.get(TRACE_ID_KEY))
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    private String extractFieldName(ConstraintViolation<?> cv) {
        String propertyPath = cv.getPropertyPath().toString();
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
