package com.gateway.runtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configures a read-only {@link JdbcTemplate} for querying the gateway schema.
 *
 * <p>The gateway-runtime shares the same PostgreSQL database and schema
 * ({@code gateway}) as the management-api. Rather than duplicating JPA entities,
 * all reads are performed via plain JDBC queries through this template.</p>
 *
 * <p>The {@link DataSource} is auto-configured by Spring Boot from the
 * {@code spring.datasource.*} properties in {@code application.yml}.</p>
 */
@Configuration
public class GatewayDataSourceConfig {

    @Bean
    public JdbcTemplate gatewayJdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // Use the gateway schema for all unqualified table references
        jdbcTemplate.execute("SET search_path TO gateway, public");
        return jdbcTemplate;
    }
}
