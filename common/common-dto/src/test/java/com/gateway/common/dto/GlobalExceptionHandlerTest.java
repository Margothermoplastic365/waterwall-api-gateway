package com.gateway.common.dto;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/test/path");
    }

    // -----------------------------------------------------------------------
    // 400 - MethodArgumentNotValidException
    // -----------------------------------------------------------------------

    @Test
    void handleMethodArgumentNotValid_shouldReturn400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "email", "bad-email",
                false, null, null, "must be a valid email");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getErrorCode()).isEqualTo("VAL_001");
        assertThat(body.getMessage()).isEqualTo("Request validation failed");
        assertThat(body.getDetails()).hasSize(1);
        assertThat(body.getDetails().get(0).getField()).isEqualTo("email");
        assertThat(body.getDetails().get(0).getMessage()).isEqualTo("must be a valid email");
        assertThat(body.getDetails().get(0).getRejectedValue()).isEqualTo("bad-email");
    }

    // -----------------------------------------------------------------------
    // 400 - ConstraintViolationException
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_shouldReturn400WithVAL002() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("createUser.arg0.name");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");
        when(violation.getInvalidValue()).thenReturn("");

        ConstraintViolationException ex =
                new ConstraintViolationException("constraint violation", Set.of(violation));

        ResponseEntity<ApiErrorResponse> response =
                handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getErrorCode()).isEqualTo("VAL_002");
        assertThat(body.getDetails()).hasSize(1);
        assertThat(body.getDetails().get(0).getField()).isEqualTo("name");
    }

    // -----------------------------------------------------------------------
    // 401 - AuthenticationException
    // -----------------------------------------------------------------------

    @Test
    void handleAuthentication_shouldReturn401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAuthentication(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getError()).isEqualTo("AUTHENTICATION_ERROR");
        assertThat(body.getErrorCode()).isEqualTo("AUTH_001");
    }

    // -----------------------------------------------------------------------
    // 403 - AccessDeniedException
    // -----------------------------------------------------------------------

    @Test
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getError()).isEqualTo("AUTHORIZATION_ERROR");
        assertThat(body.getErrorCode()).isEqualTo("AUTHZ_001");
    }

    // -----------------------------------------------------------------------
    // 404 - EntityNotFoundException
    // -----------------------------------------------------------------------

    @Test
    void handleEntityNotFound_shouldReturn404() {
        EntityNotFoundException ex = new EntityNotFoundException("User not found");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleEntityNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getError()).isEqualTo("NOT_FOUND");
        assertThat(body.getErrorCode()).isEqualTo("NOT_001");
        assertThat(body.getMessage()).isEqualTo("User not found");
    }

    // -----------------------------------------------------------------------
    // 404 - NoSuchElementException
    // -----------------------------------------------------------------------

    @Test
    void handleNoSuchElement_shouldReturn404() {
        NoSuchElementException ex = new NoSuchElementException("Element missing");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNoSuchElement(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getError()).isEqualTo("NOT_FOUND");
        assertThat(body.getErrorCode()).isEqualTo("NOT_001");
        assertThat(body.getMessage()).isEqualTo("Element missing");
    }

    // -----------------------------------------------------------------------
    // 409 - DataIntegrityViolationException
    // -----------------------------------------------------------------------

    @Test
    void handleDataIntegrityViolation_shouldReturn409() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("Duplicate key");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getError()).isEqualTo("CONFLICT");
        assertThat(body.getErrorCode()).isEqualTo("CONFLICT_001");
    }

    // -----------------------------------------------------------------------
    // 429 - RateLimitExceededException
    // -----------------------------------------------------------------------

    @Test
    void handleRateLimitExceeded_shouldReturn429WithHeaders() {
        Instant resetAt = Instant.now().plusSeconds(60);
        RateLimitExceededException ex =
                new RateLimitExceededException("api-key-abc", 100, 0, resetAt);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleRateLimitExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(429);
        assertThat(body.getError()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.getErrorCode()).isEqualTo("RATE_001");

        // Verify rate-limit headers
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Reset"))
                .isEqualTo(String.valueOf(resetAt.getEpochSecond()));
    }

    // -----------------------------------------------------------------------
    // 500 - Uncaught Exception
    // -----------------------------------------------------------------------

    @Test
    void handleAllUncaught_shouldReturn500() {
        when(request.getMethod()).thenReturn("POST");

        Exception ex = new RuntimeException("something broke");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAllUncaught(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getErrorCode()).isEqualTo("SYS_001");
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred");
    }
}
