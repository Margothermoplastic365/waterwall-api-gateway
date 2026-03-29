package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.dto.ApiDeploymentRequest;
import com.gateway.management.dto.ApiDeploymentResponse;
import com.gateway.management.dto.EnvironmentResponse;
import com.gateway.management.entity.ApiDeploymentEntity;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.EnvironmentEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.repository.ApiDeploymentRepository;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.EnvironmentRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final ApiDeploymentRepository apiDeploymentRepository;
    private final ApiRepository apiRepository;
    private final RouteRepository routeRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> listEnvironments() {
        return environmentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironment(String slug) {
        EnvironmentEntity entity = environmentRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Environment not found: " + slug));
        return toResponse(entity);
    }

    @Transactional
    public ApiDeploymentResponse deployApi(ApiDeploymentRequest request) {
        ApiEntity api = apiRepository.findById(request.getApiId())
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + request.getApiId()));

        EnvironmentEntity env = environmentRepository.findBySlug(request.getTargetEnvironment())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Environment not found: " + request.getTargetEnvironment()));

        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID deployedBy = currentUserId != null ? UUID.fromString(currentUserId) : null;

        // Build config snapshot from current API state
        String configSnapshot = buildConfigSnapshot(api);

        // Upsert deployment
        ApiDeploymentEntity deployment = apiDeploymentRepository
                .findByApiIdAndEnvironmentSlug(request.getApiId(), request.getTargetEnvironment())
                .orElse(ApiDeploymentEntity.builder()
                        .apiId(request.getApiId())
                        .environmentSlug(request.getTargetEnvironment())
                        .build());

        deployment.setStatus("DEPLOYED");
        deployment.setConfigSnapshot(configSnapshot);
        if (request.getUpstreamUrl() != null && !request.getUpstreamUrl().isBlank()) {
            deployment.setUpstreamOverrides("{\"baseUrl\":\"" + request.getUpstreamUrl().replace("\"", "\\\"") + "\"}");

            // Update all routes for this API with the new upstream URL
            List<RouteEntity> routes = routeRepository.findByApiId(request.getApiId());
            for (RouteEntity route : routes) {
                route.setUpstreamUrl(request.getUpstreamUrl());
                routeRepository.save(route);
            }
            log.info("Updated {} routes upstream URL to: {}", routes.size(), request.getUpstreamUrl());
        }
        deployment.setDeployedBy(deployedBy);
        deployment.setDeployedAt(Instant.now());

        ApiDeploymentEntity saved = apiDeploymentRepository.save(deployment);
        log.info("API deployed: apiId={}, env={}", api.getId(), env.getSlug());

        // Notify gateway-runtime to reload config with new upstream URLs
        eventPublisher.publishConfigRefresh();
        publishDomainEvent("api.deployed", saved.getId().toString());

        return toDeploymentResponse(saved, api.getName());
    }

    @Transactional(readOnly = true)
    public List<ApiDeploymentResponse> getDeploymentsForApi(UUID apiId) {
        return apiDeploymentRepository.findByApiId(apiId).stream()
                .map(d -> {
                    String apiName = apiRepository.findById(d.getApiId())
                            .map(ApiEntity::getName)
                            .orElse("Unknown");
                    return toDeploymentResponse(d, apiName);
                })
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String buildConfigSnapshot(ApiEntity api) {
        return "{\"apiId\":\"" + api.getId() + "\",\"name\":\"" + api.getName()
                + "\",\"version\":\"" + api.getVersion() + "\",\"status\":\"" + api.getStatus() + "\"}";
    }

    private EnvironmentResponse toResponse(EnvironmentEntity entity) {
        long apiCount = apiDeploymentRepository.countByEnvironmentSlug(entity.getSlug());
        return EnvironmentResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .description(entity.getDescription())
                .config(entity.getConfig())
                .apiCount((int) apiCount)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ApiDeploymentResponse toDeploymentResponse(ApiDeploymentEntity entity, String apiName) {
        return ApiDeploymentResponse.builder()
                .deploymentId(entity.getId())
                .apiId(entity.getApiId())
                .apiName(apiName)
                .environment(entity.getEnvironmentSlug())
                .status(entity.getStatus())
                .upstreamUrl(entity.getUpstreamOverrides())
                .deployedAt(entity.getDeployedAt())
                .deployedBy(entity.getDeployedBy() != null ? entity.getDeployedBy().toString() : null)
                .build();
    }

    private void publishDomainEvent(String eventType, String resourceId) {
        String actorId = SecurityContextHelper.getCurrentUserId();
        eventPublisher.publish(
                RabbitMQExchanges.PLATFORM_EVENTS,
                eventType,
                new EnvironmentDomainEvent(eventType, actorId, resourceId));
    }

    // ── Inner event class ────────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class EnvironmentDomainEvent extends BaseEvent {
        private String resourceId;

        EnvironmentDomainEvent(String eventType, String actorId, String resourceId) {
            super(eventType, actorId, null);
            this.resourceId = resourceId;
        }
    }
}
