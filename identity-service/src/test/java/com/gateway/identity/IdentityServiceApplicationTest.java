package com.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context of the
 * identity-service loads successfully.
 *
 * <p>This test requires PostgreSQL and RabbitMQ to be available.
 * Run with an appropriate profile that configures datasource and
 * AMQP connection properties.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class IdentityServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot application context starts
        // without errors. If any bean fails to initialize, this test
        // will fail with a descriptive error message.
    }
}
