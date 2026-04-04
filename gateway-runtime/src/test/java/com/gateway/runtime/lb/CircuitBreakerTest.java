package com.gateway.runtime.lb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // threshold=5, resetTimeout=60s
        circuitBreaker = new CircuitBreaker(5, 60);
    }

    @Test
    void shouldStartInClosedState() {
        assertThat(circuitBreaker.getState("http://host1:8080"))
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldOpenAfterFailureThreshold() {
        String url = "http://host1:8080";

        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure(url);
        }

        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldReturnOpenWhenCircuitIsOpen() {
        String url = "http://host1:8080";

        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure(url);
        }

        assertThat(circuitBreaker.isOpen(url)).isTrue();
    }

    @Test
    void shouldTransitionToHalfOpenAfterTimeout() throws Exception {
        String url = "http://host1:8080";

        // Open the circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure(url);
        }
        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.OPEN);

        // Manipulate lastFailureTime to simulate timeout expiry
        ConcurrentHashMap<String, CircuitBreaker.CircuitState> circuits = getCircuitsMap();
        CircuitBreaker.CircuitState state = circuits.get(url);
        Field lastFailureTimeField = CircuitBreaker.CircuitState.class.getDeclaredField("lastFailureTime");
        lastFailureTimeField.setAccessible(true);
        lastFailureTimeField.set(state, Instant.now().minusSeconds(120));

        // After timeout, getState triggers HALF_OPEN transition
        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void shouldCloseOnSuccessInHalfOpen() throws Exception {
        String url = "http://host1:8080";

        // Open the circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure(url);
        }

        // Force transition to HALF_OPEN via timeout manipulation
        ConcurrentHashMap<String, CircuitBreaker.CircuitState> circuits = getCircuitsMap();
        CircuitBreaker.CircuitState state = circuits.get(url);
        Field lastFailureTimeField = CircuitBreaker.CircuitState.class.getDeclaredField("lastFailureTime");
        lastFailureTimeField.setAccessible(true);
        lastFailureTimeField.set(state, Instant.now().minusSeconds(120));

        // Trigger HALF_OPEN transition
        circuitBreaker.isOpen(url);
        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Record success -> should close
        circuitBreaker.recordSuccess(url);
        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldResetFailureCountOnSuccess() {
        String url = "http://host1:8080";

        // Record some failures (below threshold)
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // Record success — resets failure count
        circuitBreaker.recordSuccess(url);

        // Now record failures again — should need full threshold to open
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // 4 failures after reset — still closed (threshold is 5)
        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldNotOpenBeforeThreshold() {
        String url = "http://host1:8080";

        // Record 4 failures (threshold is 5)
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure(url);
        }

        assertThat(circuitBreaker.getState(url)).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.isOpen(url)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CircuitBreaker.CircuitState> getCircuitsMap() throws Exception {
        Field circuitsField = CircuitBreaker.class.getDeclaredField("circuits");
        circuitsField.setAccessible(true);
        return (ConcurrentHashMap<String, CircuitBreaker.CircuitState>) circuitsField.get(circuitBreaker);
    }
}
