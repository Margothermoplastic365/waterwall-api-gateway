package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.GatewayNodeResponse;
import com.gateway.management.entity.GatewayNodeEntity;
import com.gateway.management.repository.GatewayNodeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterManagementService {

    private static final String CLUSTER_EXCHANGE = "cluster.events";
    private static final String CLUSTER_QUEUE = "management.cluster.events";

    private final GatewayNodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    // ── RabbitMQ bindings ─────────────────────────────────────────────

    @Bean
    public FanoutExchange clusterEventsExchange() {
        return new FanoutExchange(CLUSTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue clusterEventsQueue() {
        return QueueBuilder.durable(CLUSTER_QUEUE).build();
    }

    @Bean
    public Binding clusterEventsBinding(Queue clusterEventsQueue, FanoutExchange clusterEventsExchange) {
        return BindingBuilder.bind(clusterEventsQueue).to(clusterEventsExchange);
    }

    // ── Event listener ────────────────────────────────────────────────

    @RabbitListener(queues = CLUSTER_QUEUE)
    @Transactional
    public void handleClusterEvent(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String eventType = json.path("eventType").asText();

            switch (eventType) {
                case "node.registered" -> handleNodeRegistered(json);
                case "node.heartbeat" -> handleNodeHeartbeat(json);
                case "node.deregistered" -> handleNodeDeregistered(json);
                default -> log.debug("Unknown cluster event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process cluster event: {}", e.getMessage(), e);
        }
    }

    private void handleNodeRegistered(JsonNode json) {
        String hostname = json.path("hostname").asText();
        String ipAddress = json.path("ipAddress").asText("");
        int port = json.path("port").asInt(8080);

        GatewayNodeEntity node = nodeRepository.findByHostname(hostname)
                .orElse(GatewayNodeEntity.builder()
                        .hostname(hostname)
                        .registeredAt(Instant.now())
                        .build());

        node.setIpAddress(ipAddress);
        node.setPort(port);
        node.setStatus("UP");
        node.setLastHeartbeat(Instant.now());
        nodeRepository.save(node);
        log.info("Registered gateway node: {}", hostname);
    }

    private void handleNodeHeartbeat(JsonNode json) {
        String hostname = json.path("hostname").asText();
        long configVersion = json.path("configVersion").asLong(0);

        nodeRepository.findByHostname(hostname).ifPresentOrElse(node -> {
            node.setLastHeartbeat(Instant.now());
            node.setConfigVersion(configVersion);
            node.setStatus("UP");
            nodeRepository.save(node);
            log.debug("Heartbeat from node: {}", hostname);
        }, () -> {
            log.warn("Heartbeat from unknown node: {}", hostname);
        });
    }

    private void handleNodeDeregistered(JsonNode json) {
        String hostname = json.path("hostname").asText();
        nodeRepository.findByHostname(hostname).ifPresent(node -> {
            node.setStatus("DOWN");
            nodeRepository.save(node);
            log.info("Deregistered gateway node: {}", hostname);
        });
    }

    // ── Scheduled stale-node check ────────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markStaleNodes() {
        Instant cutoff = Instant.now().minusSeconds(90);
        int marked = nodeRepository.markStaleNodesDown(cutoff);
        if (marked > 0) {
            log.warn("Marked {} gateway node(s) as DOWN (no heartbeat for 90s)", marked);
        }
    }

    // ── Query methods ─────────────────────────────────────────────────

    public List<GatewayNodeResponse> listNodes() {
        return nodeRepository.findAll().stream().map(this::toResponse).toList();
    }

    public GatewayNodeResponse getNode(String hostname) {
        GatewayNodeEntity entity = nodeRepository.findByHostname(hostname)
                .orElseThrow(() -> new EntityNotFoundException("Gateway node not found: " + hostname));
        return toResponse(entity);
    }

    private GatewayNodeResponse toResponse(GatewayNodeEntity entity) {
        return GatewayNodeResponse.builder()
                .id(entity.getId())
                .hostname(entity.getHostname())
                .ipAddress(entity.getIpAddress())
                .port(entity.getPort())
                .configVersion(entity.getConfigVersion())
                .status(entity.getStatus())
                .lastHeartbeat(entity.getLastHeartbeat())
                .registeredAt(entity.getRegisteredAt())
                .build();
    }
}
