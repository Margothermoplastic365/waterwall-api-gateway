package com.gateway.runtime.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrictRateLimitServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private StrictRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new StrictRateLimitService(jdbcTemplate);
    }

    @Test
    void shouldReturnCountWhenWithinLimit() {
        when(jdbcTemplate.queryForList(any(String.class), eq(Integer.class),
                any(), any(), any()))
                .thenReturn(List.of(1));

        int result = rateLimitService.incrementAndCheck("ratelimit:app1:api1", 60, 100);

        assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnNegativeOneWhenLimitExceeded() {
        // Empty list means the RETURNING clause returned no rows (limit exceeded)
        when(jdbcTemplate.queryForList(any(String.class), eq(Integer.class),
                any(), any(), any()))
                .thenReturn(Collections.emptyList());

        int result = rateLimitService.incrementAndCheck("ratelimit:app1:api1", 60, 100);

        assertThat(result).isEqualTo(-1);
    }

    @Test
    void shouldHandleDataAccessException() {
        when(jdbcTemplate.queryForList(any(String.class), eq(Integer.class),
                any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        int result = rateLimitService.incrementAndCheck("ratelimit:app1:api1", 60, 100);

        // Fails open — returns 0
        assertThat(result).isZero();
    }
}
