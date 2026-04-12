package com.gateway.runtime.lb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-upstream circuit breaker with three states: CLOSED (normal), OPEN (failing), HALF_OPEN (testing).
 * When a circuit is OPEN, requests should not be sent to that upstream.
 * After the reset timeout, the circuit transitions to HALF_OPEN to allow one test request.
 */
@Slf4j
@Component
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long resetTimeoutSeconds;

    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public CircuitBreaker(
            @Value("${gateway.runtime.lb.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${gateway.runtime.lb.circuit-breaker.reset-timeout-seconds:60}") long resetTimeoutSeconds) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutSeconds = resetTimeoutSeconds;
    }

    /**
     * Check if the circuit for the given upstream URL is open (should NOT call this upstream).
     */
    public boolean isOpen(String url) {
        CircuitState state = circuits.get(url);
        if (state == null) return false;

        if (state.state == State.OPEN) {
            if (Instant.now().isAfter(state.lastFailureTime.plusSeconds(resetTimeoutSeconds))) {
                state.state = State.HALF_OPEN;
                log.debug("Circuit breaker for {} transitioning to HALF_OPEN", url);
                return false;
            }
            return true;
        }
        return false;
    }

    public void recordSuccess(String url) {
        CircuitState state = circuits.get(url);
        if (state == null) return;
        state.state = State.CLOSED;
        state.failureCount = 0;
    }

    public void recordFailure(String url) {
        CircuitState state = circuits.computeIfAbsent(url, k -> new CircuitState());
        state.failureCount++;
        state.lastFailureTime = Instant.now();

        if (state.state == State.HALF_OPEN) {
            state.state = State.OPEN;
            log.warn("Circuit breaker for {} re-opening after failed test request", url);
        } else if (state.failureCount >= failureThreshold) {
            state.state = State.OPEN;
            log.warn("Circuit breaker for {} opened after {} failures", url, state.failureCount);
        }
    }

    /**
     * Get the current state of the circuit for the given URL.
     */
    public State getState(String url) {
        CircuitState state = circuits.get(url);
        if (state == null) {
            return State.CLOSED;
        }
        // Trigger potential HALF_OPEN transition
        isOpen(url);
        return state.state;
    }

    /**
     * Get all circuit states (for monitoring).
     */
    public Map<String, CircuitState> getAllCircuits() {
        return Map.copyOf(circuits);
    }

    public static class CircuitState {
        volatile State state = State.CLOSED;
        volatile int failureCount = 0;
        volatile Instant lastFailureTime = Instant.now();

        public State getState() {
            return state;
        }

        public int getFailureCount() {
            return failureCount;
        }
    }
}
