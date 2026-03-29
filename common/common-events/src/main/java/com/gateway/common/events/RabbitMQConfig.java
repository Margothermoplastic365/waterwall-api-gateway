package com.gateway.common.events;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Declares all RabbitMQ exchanges used by the API Gateway Platform.
 * Every service that includes common-events will auto-create these exchanges on startup.
 */
@Configuration
public class RabbitMQConfig {

    // ── Topic Exchanges ─────────────────────────────────────────────────

    @Bean
    public TopicExchange platformEventsExchange() {
        return new TopicExchange(RabbitMQExchanges.PLATFORM_EVENTS, true, false);
    }

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(RabbitMQExchanges.NOTIFICATIONS, true, false);
    }

    @Bean
    public TopicExchange analyticsIngestExchange() {
        return new TopicExchange(RabbitMQExchanges.ANALYTICS_INGEST, true, false);
    }

    @Bean
    public TopicExchange auditEventsExchange() {
        return new TopicExchange(RabbitMQExchanges.AUDIT_EVENTS, true, false);
    }

    // ── Fanout Exchanges ────────────────────────────────────────────────

    @Bean
    public FanoutExchange cacheInvalidateExchange() {
        return new FanoutExchange(RabbitMQExchanges.CACHE_INVALIDATE, true, false);
    }

    @Bean
    public FanoutExchange rateLimitSyncExchange() {
        return new FanoutExchange(RabbitMQExchanges.RATELIMIT_SYNC, true, false);
    }

    @Bean
    public FanoutExchange lockoutSyncExchange() {
        return new FanoutExchange(RabbitMQExchanges.LOCKOUT_SYNC, true, false);
    }

    @Bean
    public FanoutExchange configRefreshExchange() {
        return new FanoutExchange(RabbitMQExchanges.CONFIG_REFRESH, true, false);
    }

    // ── Message Converter ───────────────────────────────────────────────

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        // Trust all packages so consumers can deserialize messages from any producer
        // without needing the exact producer class on the classpath
        org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper typeMapper =
                new org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
