package com.gateway.runtime.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Event Gateway Service — manages connections to an external RabbitMQ broker
 * (the customer's broker) and provides publish/subscribe/queue-management
 * capabilities for the event gateway proxy.
 */
@Slf4j
@Service
public class EventGatewayService {

    private static final String DLX_ARG = "x-dead-letter-exchange";
    private static final String DLX_RK_ARG = "x-dead-letter-routing-key";

    private final CachingConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final String deadLetterExchange;
    private final int maxMessageSizeBytes;

    private final ConcurrentHashMap<String, SimpleMessageListenerContainer> activeListeners =
            new ConcurrentHashMap<>();

    public EventGatewayService(
            @Value("${gateway.events.default-broker-url:amqp://localhost:5672}") String brokerUrl,
            @Value("${gateway.events.dead-letter-exchange:gateway.dlx}") String deadLetterExchange,
            @Value("${gateway.events.max-message-size-bytes:1048576}") int maxMessageSizeBytes) {

        this.deadLetterExchange = deadLetterExchange;
        this.maxMessageSizeBytes = maxMessageSizeBytes;

        this.connectionFactory = new CachingConnectionFactory();
        try {
            this.connectionFactory.setUri(brokerUrl);
        } catch (Exception e) {
            log.warn("Failed to parse broker URI '{}', using defaults: {}", brokerUrl, e.getMessage());
        }
        this.connectionFactory.setRequestedHeartBeat(30);

        this.rabbitTemplate = new RabbitTemplate(connectionFactory);
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
        this.rabbitAdmin.afterPropertiesSet();

        ensureDeadLetterInfrastructure();
        log.info("EventGatewayService initialised — broker={}, DLX={}", brokerUrl, deadLetterExchange);
    }

    // ── Publish ──────────────────────────────────────────────────────────

    /**
     * Publish a message to the specified exchange with the given routing key.
     */
    public void publishToExchange(String exchange, String routingKey, String message) {
        if (message.getBytes(StandardCharsets.UTF_8).length > maxMessageSizeBytes) {
            throw new IllegalArgumentException(
                    "Message size exceeds maximum allowed size of " + maxMessageSizeBytes + " bytes");
        }
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.debug("Published message to exchange={} routingKey={}", exchange, routingKey);
    }

    // ── Subscribe ────────────────────────────────────────────────────────

    /**
     * Subscribe to a queue. Returns a subscription ID that can be used to unsubscribe.
     */
    public String subscribeToQueue(String queue, Consumer<String> handler) {
        String subscriptionId = queue + "-" + System.nanoTime();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queue);
        container.setMessageListener((MessageListener) message -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                handler.accept(body);
            } catch (Exception e) {
                log.error("Error processing message from queue={}: {}", queue, e.getMessage(), e);
            }
        });
        container.start();

        activeListeners.put(subscriptionId, container);
        log.info("Subscribed to queue={} subscriptionId={}", queue, subscriptionId);
        return subscriptionId;
    }

    /**
     * Unsubscribe by subscription ID.
     */
    public boolean unsubscribe(String subscriptionId) {
        SimpleMessageListenerContainer container = activeListeners.remove(subscriptionId);
        if (container != null) {
            container.stop();
            container.destroy();
            log.info("Unsubscribed subscriptionId={}", subscriptionId);
            return true;
        }
        return false;
    }

    // ── Queue Management ─────────────────────────────────────────────────

    /**
     * Create a queue with optional arguments (TTL, max-length, etc.).
     * Automatically configures dead-letter exchange if not already specified.
     */
    public String createQueue(String name, Map<String, Object> args) {
        Map<String, Object> queueArgs = new ConcurrentHashMap<>();
        if (args != null) {
            queueArgs.putAll(args);
        }

        // Set dead letter exchange if not already specified
        if (!queueArgs.containsKey(DLX_ARG)) {
            queueArgs.put(DLX_ARG, deadLetterExchange);
            queueArgs.put(DLX_RK_ARG, name + ".dlq");
        }

        Queue queue = new Queue(name, true, false, false, queueArgs);
        String actualName = rabbitAdmin.declareQueue(queue);

        // Also create the dead-letter queue for this queue
        String dlqName = name + ".dlq";
        Queue dlq = new Queue(dlqName, true, false, false);
        rabbitAdmin.declareQueue(dlq);

        Binding dlqBinding = BindingBuilder
                .bind(dlq)
                .to(new DirectExchange(deadLetterExchange))
                .with(name + ".dlq");
        rabbitAdmin.declareBinding(dlqBinding);

        log.info("Created queue={} with DLQ={}", name, dlqName);
        return actualName;
    }

    /**
     * List queues — returns queue names from active subscriptions and any the admin can see.
     */
    public java.util.Properties getQueueProperties(String queueName) {
        return rabbitAdmin.getQueueProperties(queueName);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private void ensureDeadLetterInfrastructure() {
        try {
            DirectExchange dlx = new DirectExchange(deadLetterExchange, true, false);
            rabbitAdmin.declareExchange(dlx);
            log.debug("Dead-letter exchange '{}' ensured", deadLetterExchange);
        } catch (Exception e) {
            log.warn("Could not declare DLX '{}': {}", deadLetterExchange, e.getMessage());
        }
    }

    /**
     * Get the underlying connection factory — used by mediation services.
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public boolean hasSubscription(String subscriptionId) {
        return activeListeners.containsKey(subscriptionId);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down EventGatewayService — stopping {} listeners", activeListeners.size());
        activeListeners.values().forEach(container -> {
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                log.warn("Error stopping listener: {}", e.getMessage());
            }
        });
        activeListeners.clear();
        connectionFactory.destroy();
    }
}
