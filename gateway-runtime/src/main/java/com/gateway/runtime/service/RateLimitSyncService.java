package com.gateway.runtime.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.gateway.common.cache.RateLimitCounter;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RateLimitSyncEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for rate-limit counter sync events from other gateway nodes and
 * applies remote increments to the local in-memory counter. Also publishes
 * local increments so peer nodes can stay in sync.
 *
 * <p>Each node uses its hostname as {@code nodeId} to avoid applying its own
 * published events.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitSyncService {

    private static final String NODE_ID = resolveHostname();

    private final RateLimitCounter rateLimitCounter;
    private final EventPublisher eventPublisher;

    /**
     * Receives rate-limit sync events from the fanout exchange.
     * Each node gets its own exclusive, auto-delete queue so every node
     * receives every broadcast.
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(exclusive = "true", autoDelete = "true"),
            exchange = @Exchange(name = "ratelimit.sync", type = "fanout")
    ))
    public void onRateLimitSync(RateLimitSyncEvent event) {
        if (event == null) {
            log.warn("Received null RateLimitSyncEvent");
            return;
        }

        // Skip events originating from this node
        if (NODE_ID.equals(event.getNodeId())) {
            log.trace("Skipping self-originated rate-limit sync for key={}", event.getKey());
            return;
        }

        log.debug("Applying remote rate-limit increment: key={}, increment={}, fromNode={}",
                event.getKey(), event.getIncrement(), event.getNodeId());

        rateLimitCounter.applyRemoteIncrement(event.getKey(), event.getIncrement());
    }

    /**
     * Publishes a local rate-limit counter increment to all peer nodes via
     * the {@code ratelimit.sync} fanout exchange.
     *
     * @param key       the rate-limit bucket key
     * @param increment the number of requests to broadcast
     */
    public void publishIncrement(String key, int increment) {
        log.debug("Publishing rate-limit sync: key={}, increment={}, nodeId={}", key, increment, NODE_ID);
        eventPublisher.publishRateLimitSync(key, increment, NODE_ID);
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + ProcessHandle.current().pid();
        }
    }
}
