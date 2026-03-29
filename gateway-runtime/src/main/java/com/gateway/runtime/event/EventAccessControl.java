package com.gateway.runtime.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Controls access to event operations (publish/subscribe) based on
 * the consumer's subscription plan and API-level configuration.
 *
 * <p>In a production deployment this component would query the management API
 * to resolve the caller's plan and check topic-level permissions.  For now it
 * provides a permissive default that can be refined later.</p>
 */
@Slf4j
@Component
public class EventAccessControl {

    /**
     * Check whether the current consumer is allowed to publish to the
     * given exchange and routing key.
     */
    public boolean canPublish(String exchange, String routingKey) {
        // TODO: look up consumer identity from security context and verify
        //       against allowed-topics in subscription plan / event API config
        log.debug("Access check — publish exchange={} routingKey={}", exchange, routingKey);
        if (exchange == null || exchange.isBlank()) {
            log.warn("Publish denied — exchange is blank");
            return false;
        }
        return true;
    }

    /**
     * Check whether the current consumer is allowed to subscribe to the
     * given queue.
     */
    public boolean canSubscribe(String queue) {
        log.debug("Access check — subscribe queue={}", queue);
        if (queue == null || queue.isBlank()) {
            log.warn("Subscribe denied — queue is blank");
            return false;
        }
        return true;
    }

    /**
     * Check whether the current consumer may access a specific topic for
     * SSE / WebSocket / polling mediation.
     */
    public boolean canAccessTopic(String topic) {
        log.debug("Access check — topic={}", topic);
        if (topic == null || topic.isBlank()) {
            return false;
        }
        return true;
    }
}
