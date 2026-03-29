package com.gateway.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers the common logging infrastructure for any
 * service that includes the {@code common-logging} module on its classpath.
 *
 * <p>Registered beans:
 * <ul>
 *   <li>{@link TraceIdFilter} — populates SLF4J MDC with traceId, spanId,
 *       parentSpanId, service, userId, orgId and propagates distributed trace
 *       headers (X-Trace-Id, X-Span-Id, X-Parent-Span-Id, X-Request-Duration)</li>
 * </ul>
 *
 * <p>This class is declared in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so that Spring Boot discovers it automatically.
 */
@AutoConfiguration
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
