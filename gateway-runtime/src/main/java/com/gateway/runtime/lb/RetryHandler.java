package com.gateway.runtime.lb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Retry handler for failed upstream requests with exponential backoff.
 * Only retries on 502, 503, 504, and connection timeout errors.
 * Does NOT retry on 4xx or successful responses.
 */
@Slf4j
@Component
public class RetryHandler {

    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(502, 503, 504);

    private final int maxRetries;
    private final long backoffMs;

    public RetryHandler(
            @Value("${gateway.runtime.lb.retry.max-retries:2}") int maxRetries,
            @Value("${gateway.runtime.lb.retry.backoff-ms:100}") long backoffMs) {
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
    }

    /**
     * Execute a request with retry logic.
     *
     * @param requestSupplier supplies the upstream request call
     * @return the response entity
     */
    public ResponseEntity<byte[]> executeWithRetry(Supplier<ResponseEntity<byte[]>> requestSupplier) {
        ResponseEntity<byte[]> response = null;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                response = requestSupplier.get();

                if (response != null && !isRetryableStatusCode(response.getStatusCode().value())) {
                    return response;
                }

                if (attempt < maxRetries) {
                    long delay = calculateBackoff(attempt);
                    log.debug("Retryable status {} received, retrying in {}ms (attempt {}/{})",
                            response != null ? response.getStatusCode().value() : "null",
                            delay, attempt + 1, maxRetries);
                    sleep(delay);
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    long delay = calculateBackoff(attempt);
                    log.debug("Request failed with exception, retrying in {}ms (attempt {}/{}): {}",
                            delay, attempt + 1, maxRetries, e.getMessage());
                    sleep(delay);
                }
            }
        }

        // All retries exhausted
        if (response != null) {
            return response;
        }
        if (lastException instanceof RuntimeException rte) {
            throw rte;
        }
        if (lastException != null) {
            throw new RuntimeException("All retries exhausted", lastException);
        }

        // Should not reach here
        return ResponseEntity.status(502)
                .body("{\"error\":\"bad_gateway\",\"message\":\"All retries exhausted\"}".getBytes());
    }

    /**
     * Check if a status code is retryable.
     */
    public boolean isRetryableStatusCode(int statusCode) {
        return RETRYABLE_STATUS_CODES.contains(statusCode);
    }

    private long calculateBackoff(int attempt) {
        // Exponential backoff: 100ms, 200ms, 400ms, ...
        return backoffMs * (1L << attempt);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
