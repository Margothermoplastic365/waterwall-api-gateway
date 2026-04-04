package com.gateway.runtime.lb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHandlerTest {

    private RetryHandler retryHandler;

    @BeforeEach
    void setUp() {
        // maxRetries=2, backoffMs=1 (minimal backoff for fast tests)
        retryHandler = new RetryHandler(2, 1);
    }

    @Test
    void shouldReturnOnFirstSuccess() {
        ResponseEntity<byte[]> ok = ResponseEntity.ok("success".getBytes());

        ResponseEntity<byte[]> result = retryHandler.executeWithRetry(() -> ok);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void shouldRetryOnRetryableStatusCode() {
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<ResponseEntity<byte[]>> supplier = () -> {
            if (callCount.getAndIncrement() == 0) {
                return ResponseEntity.status(502).body("bad gateway".getBytes());
            }
            return ResponseEntity.ok("success".getBytes());
        };

        ResponseEntity<byte[]> result = retryHandler.executeWithRetry(supplier);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void shouldRetryOnException() {
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<ResponseEntity<byte[]>> supplier = () -> {
            if (callCount.getAndIncrement() == 0) {
                throw new RuntimeException("Connection refused");
            }
            return ResponseEntity.ok("success".getBytes());
        };

        ResponseEntity<byte[]> result = retryHandler.executeWithRetry(supplier);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void shouldGiveUpAfterMaxRetries() {
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<ResponseEntity<byte[]>> supplier = () -> {
            callCount.incrementAndGet();
            return ResponseEntity.status(502).body("bad gateway".getBytes());
        };

        ResponseEntity<byte[]> result = retryHandler.executeWithRetry(supplier);

        // maxRetries=2 means 3 total attempts (initial + 2 retries)
        assertThat(callCount.get()).isEqualTo(3);
        assertThat(result.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    void shouldNotRetryNonRetryableStatusCode() {
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<ResponseEntity<byte[]>> supplier = () -> {
            callCount.incrementAndGet();
            return ResponseEntity.status(400).body("bad request".getBytes());
        };

        ResponseEntity<byte[]> result = retryHandler.executeWithRetry(supplier);

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(result.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void shouldIdentifyRetryableStatusCodes() {
        assertThat(retryHandler.isRetryableStatusCode(502)).isTrue();
        assertThat(retryHandler.isRetryableStatusCode(503)).isTrue();
        assertThat(retryHandler.isRetryableStatusCode(504)).isTrue();

        assertThat(retryHandler.isRetryableStatusCode(200)).isFalse();
        assertThat(retryHandler.isRetryableStatusCode(400)).isFalse();
        assertThat(retryHandler.isRetryableStatusCode(500)).isFalse();
    }

    @Test
    void shouldRethrowRuntimeExceptionAfterMaxRetries() {
        Supplier<ResponseEntity<byte[]>> supplier = () -> {
            throw new RuntimeException("Persistent failure");
        };

        assertThatThrownBy(() -> retryHandler.executeWithRetry(supplier))
                .isInstanceOf(RuntimeException.class);
    }
}
