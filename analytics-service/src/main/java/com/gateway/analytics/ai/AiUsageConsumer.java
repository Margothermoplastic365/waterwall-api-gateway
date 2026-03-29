package com.gateway.analytics.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Consumes AI usage events from RabbitMQ and persists them to the
 * {@code gateway.ai_token_usage} table for observability and billing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageConsumer {

    private final JdbcTemplate jdbcTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "analytics-service.ai-usage", durable = "true"),
            exchange = @Exchange(value = "analytics.ingest", type = "topic"),
            key = "ai.usage.*"
    ))
    public void handleAiUsageEvent(org.springframework.amqp.core.Message rawMessage) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.findAndRegisterModules();
            AiUsageEvent event = mapper.readValue(rawMessage.getBody(), AiUsageEvent.class);
            log.debug("Received AI usage event: consumer={} provider={} model={} tokens={}",
                    event.getConsumerId(), event.getProvider(), event.getModel(), event.getTotalTokens());

            String sql = """
                INSERT INTO gateway.ai_token_usage
                    (consumer_id, provider, model, prompt_tokens, completion_tokens,
                     total_tokens, cost, request_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            Instant ts = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();

            jdbcTemplate.update(sql,
                    event.getConsumerId(),
                    event.getProvider(),
                    event.getModel(),
                    event.getPromptTokens(),
                    event.getCompletionTokens(),
                    event.getTotalTokens(),
                    event.getCost(),
                    event.getRequestId(),
                    Timestamp.from(ts));

            log.debug("Persisted AI usage: requestId={}", event.getRequestId());
        } catch (Exception ex) {
            log.error("Failed to process AI usage event: {}", ex.getMessage(), ex);
        }
    }
}
