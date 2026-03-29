package com.gateway.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages this gateway node's lifecycle within the cluster.
 * Registers on startup, sends periodic heartbeats, and deregisters on shutdown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private static final String CLUSTER_EXCHANGE = "cluster.events";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RouteConfigService routeConfigService;

    private String hostname;
    private String ipAddress;
    private long startTime;

    @Bean
    public FanoutExchange gatewayClusterExchange() {
        return new FanoutExchange(CLUSTER_EXCHANGE, true, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostname = localHost.getHostName();
            ipAddress = localHost.getHostAddress();
        } catch (Exception e) {
            hostname = "unknown-" + ProcessHandle.current().pid();
            ipAddress = "127.0.0.1";
        }
        startTime = System.currentTimeMillis();

        publishEvent("node.registered", Map.of(
                "hostname", hostname,
                "ipAddress", ipAddress,
                "port", 8080
        ));
        log.info("Gateway node registered: hostname={}, ip={}", hostname, ipAddress);
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void heartbeat() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long configVersion = routeConfigService.getConfigVersion();

        publishEvent("node.heartbeat", Map.of(
                "hostname", hostname,
                "ipAddress", ipAddress,
                "configVersion", configVersion,
                "uptimeMs", uptimeMs,
                "version", "1.0.0"
        ));
        log.debug("Published heartbeat: hostname={}, configVersion={}, uptimeMs={}",
                hostname, configVersion, uptimeMs);
    }

    @PreDestroy
    public void onShutdown() {
        publishEvent("node.deregistered", Map.of(
                "hostname", hostname != null ? hostname : "unknown"
        ));
        log.info("Gateway node deregistered: hostname={}", hostname);
    }

    private void publishEvent(String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> event = new LinkedHashMap<>(data);
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(CLUSTER_EXCHANGE, "", json);
        } catch (Exception e) {
            log.error("Failed to publish cluster event '{}': {}", eventType, e.getMessage());
        }
    }
}
