package com.gateway.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitExceededExceptionTest {

    @Test
    void shouldFormatMessageCorrectly() {
        Instant resetAt = Instant.parse("2026-04-03T12:00:00Z");

        RateLimitExceededException ex = new RateLimitExceededException(
                "api-key-123", 100, 0, resetAt);

        assertThat(ex.getMessage())
                .contains("api-key-123")
                .contains("limit=100")
                .contains("remaining=0")
                .contains("2026-04-03T12:00:00Z");
    }

    @Test
    void shouldExposeGetters() {
        Instant resetAt = Instant.parse("2026-04-03T15:30:00Z");

        RateLimitExceededException ex = new RateLimitExceededException(
                "client-ip-10.0.0.1", 50, 3, resetAt);

        assertThat(ex.getKey()).isEqualTo("client-ip-10.0.0.1");
        assertThat(ex.getLimit()).isEqualTo(50);
        assertThat(ex.getRemaining()).isEqualTo(3);
        assertThat(ex.getResetAt()).isEqualTo(resetAt);
    }

    @Test
    void shouldBeRuntimeException() {
        RateLimitExceededException ex = new RateLimitExceededException(
                "key", 10, 0, Instant.now());

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
