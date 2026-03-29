package com.gateway.common.dto;

import lombok.Getter;

import java.time.Instant;

/**
 * Thrown when a client exceeds its configured rate limit.
 * Carries contextual information so the exception handler can build
 * an informative 429 response with Retry-After semantics.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final long limit;
    private final long remaining;
    private final Instant resetAt;

    public RateLimitExceededException(String key, long limit, long remaining, Instant resetAt) {
        super(String.format("Rate limit exceeded for key '%s': limit=%d, remaining=%d, resets at %s",
                key, limit, remaining, resetAt));
        this.key = key;
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
    }
}
