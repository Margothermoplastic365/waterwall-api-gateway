package com.gateway.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitCounterTest {

    private RateLimitCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RateLimitCounter();
    }

    @Test
    void shouldIncrementWithinLimit() {
        assertThat(counter.incrementAndGet("app:100", 5)).isEqualTo(1);
        assertThat(counter.incrementAndGet("app:100", 5)).isEqualTo(2);
        assertThat(counter.incrementAndGet("app:100", 5)).isEqualTo(3);
    }

    @Test
    void shouldReturnNegativeOneWhenOverLimit() {
        int limit = 2;
        counter.incrementAndGet("app:100", limit); // 1
        counter.incrementAndGet("app:100", limit); // 2

        int result = counter.incrementAndGet("app:100", limit); // 3 > 2
        assertThat(result).isEqualTo(-1);
    }

    @Test
    void shouldReturnZeroForUnknownKey() {
        assertThat(counter.get("nonexistent:999")).isZero();
    }

    @Test
    void shouldResetCounter() {
        counter.incrementAndGet("app:100", 10);
        counter.incrementAndGet("app:100", 10);
        assertThat(counter.get("app:100")).isEqualTo(2);

        counter.reset("app:100");
        assertThat(counter.get("app:100")).isZero();
    }

    @Test
    void shouldApplyRemoteIncrement() {
        counter.incrementAndGet("app:100", 100); // local count = 1
        counter.applyRemoteIncrement("app:100", 5);
        assertThat(counter.get("app:100")).isEqualTo(6);
    }

    @Test
    void shouldApplyRemoteIncrementCreatesIfAbsent() {
        counter.applyRemoteIncrement("new:200", 7);
        assertThat(counter.get("new:200")).isEqualTo(7);
    }

    @Test
    void shouldReportCorrectSize() {
        assertThat(counter.size()).isZero();

        counter.incrementAndGet("a:1", 10);
        counter.incrementAndGet("b:2", 10);
        counter.incrementAndGet("c:3", 10);
        assertThat(counter.size()).isEqualTo(3);
    }

    @Test
    void shouldCleanupExpiredWindows() {
        // Use an epoch far in the past (epoch second = 1000)
        String expiredKey = "consumer:1000";
        counter.incrementAndGet(expiredKey, 10);
        assertThat(counter.get(expiredKey)).isEqualTo(1);

        counter.cleanupExpiredWindows();

        assertThat(counter.get(expiredKey)).isZero();
        assertThat(counter.size()).isZero();
    }

    @Test
    void shouldNotCleanupRecentWindows() {
        long currentEpochSecond = System.currentTimeMillis() / 1000;
        String recentKey = "consumer:" + currentEpochSecond;
        counter.incrementAndGet(recentKey, 10);

        counter.cleanupExpiredWindows();

        assertThat(counter.get(recentKey)).isEqualTo(1);
        assertThat(counter.size()).isEqualTo(1);
    }

    @Test
    void shouldHandleNonNumericWindowSuffix() {
        String nonNumericKey = "consumer:abc";
        counter.incrementAndGet(nonNumericKey, 10);

        counter.cleanupExpiredWindows();

        // Non-numeric suffix should NOT be removed
        assertThat(counter.get(nonNumericKey)).isEqualTo(1);
        assertThat(counter.size()).isEqualTo(1);
    }
}
