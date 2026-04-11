package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.dto.ApiResponse;
import com.gateway.management.dto.ApiGatewayConfigRequest;
import com.gateway.management.dto.AuthPolicyRequest;
import com.gateway.management.dto.CreateApiRequest;
import com.gateway.management.dto.RouteResponse;
import com.gateway.management.dto.UpdateApiRequest;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiService {

    private final ApiRepository apiRepository;
    private final RouteRepository routeRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public ApiResponse createApi(CreateApiRequest request) {
        String currentUserId = SecurityContextHelper.getCurrentUserId();
        String currentOrgId = SecurityContextHelper.getCurrentOrgId();

        ApiEntity entity = ApiEntity.builder()
                .name(request.getName())
                .contextPath(request.getContextPath())
                .version(request.getVersion())
                .description(request.getDescription())
                .status(ApiStatus.CREATED)
                .visibility(request.getVisibility())
                .protocolType(request.getProtocolType())
                .tags(request.getTags())
                .category(request.getCategory())
                .backendBaseUrl(request.getBackendBaseUrl())
                .createdBy(currentUserId != null ? UUID.fromString(currentUserId) : null)
                .orgId(currentOrgId != null ? UUID.fromString(currentOrgId) : null)
                .build();

        if (entity.getContextPath() == null || entity.getContextPath().isBlank()) {
            entity.setContextPath(generateContextPath(entity.getName()));
        }

        ApiEntity saved = apiRepository.save(entity);
        // Set api_group_id to own id for new APIs (first version)
        saved.setApiGroupId(saved.getId());
        saved.setApiGroupName(saved.getName());
        saved = apiRepository.save(saved);
        log.info("API created: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved, Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public Page<ApiResponse> listApis(String search, String status, String category, Pageable pageable) {
        Specification<ApiEntity> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("name")), pattern),
                            cb.like(cb.lower(root.get("description")), pattern)
                    ));
        }

        if (status != null && !status.isBlank()) {
            ApiStatus apiStatus = ApiStatus.valueOf(status.toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), apiStatus));
        } else {
            // Exclude RETIRED (soft-deleted) APIs by default
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), ApiStatus.RETIRED));
        }

        if (category != null && !category.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }

        return apiRepository.findAll(spec, pageable)
                .map(entity -> toResponse(entity, Collections.emptyList()));
    }

    @Transactional(readOnly = true)
    public ApiResponse getApi(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        List<RouteEntity> routes = routeRepository.findByApiId(id);
        return toResponse(entity, routes);
    }

    @Transactional
    public ApiResponse updateApi(UUID id, UpdateApiRequest request) {
        ApiEntity entity = findApiOrThrow(id);

        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            entity.setVisibility(request.getVisibility());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }
        if (request.getCategory() != null) {
            entity.setCategory(request.getCategory());
        }
        if (request.getBackendBaseUrl() != null) {
            entity.setBackendBaseUrl(request.getBackendBaseUrl());
        }
        if (request.getContextPath() != null) {
            entity.setContextPath(request.getContextPath());
        }

        ApiEntity saved = apiRepository.save(entity);
        List<RouteEntity> routes = routeRepository.findByApiId(id);
        log.info("API updated: id={}", saved.getId());
        return toResponse(saved, routes);
    }

    @Transactional
    public void deleteApi(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        entity.setStatus(ApiStatus.RETIRED);
        apiRepository.save(entity);
        log.info("API soft-deleted (retired): id={}", id);
    }

    @Transactional
    public ApiResponse publishApi(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        if (entity.getStatus() != ApiStatus.CREATED && entity.getStatus() != ApiStatus.DRAFT && entity.getStatus() != ApiStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "Cannot publish API in status " + entity.getStatus() + "; expected CREATED, DRAFT, or IN_REVIEW");
        }

        entity.setStatus(ApiStatus.PUBLISHED);
        ApiEntity saved = apiRepository.save(entity);

        publishDomainEvent("api.published", saved.getId().toString());
        log.info("API published: id={}", saved.getId());

        List<RouteEntity> routes = routeRepository.findByApiId(id);
        return toResponse(saved, routes);
    }

    @Transactional
    public ApiResponse deprecateApi(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        if (entity.getStatus() != ApiStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Cannot deprecate API in status " + entity.getStatus() + "; expected PUBLISHED");
        }

        entity.setStatus(ApiStatus.DEPRECATED);
        ApiEntity saved = apiRepository.save(entity);

        publishDomainEvent("api.deprecated", saved.getId().toString());
        log.info("API deprecated: id={}", saved.getId());

        List<RouteEntity> routes = routeRepository.findByApiId(id);
        return toResponse(saved, routes);
    }

    @Transactional
    public ApiResponse retireApi(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        if (entity.getStatus() != ApiStatus.DEPRECATED && entity.getStatus() != ApiStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Cannot retire API in status " + entity.getStatus() + "; expected PUBLISHED or DEPRECATED");
        }

        entity.setStatus(ApiStatus.RETIRED);
        ApiEntity saved = apiRepository.save(entity);

        publishDomainEvent("api.retired", saved.getId().toString());
        log.info("API retired: id={}", saved.getId());

        List<RouteEntity> routes = routeRepository.findByApiId(id);
        return toResponse(saved, routes);
    }

    @Transactional(readOnly = true)
    public AuthPolicyRequest getAuthPolicy(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        List<RouteEntity> routes = routeRepository.findByApiId(id);

        // Collect all unique auth types from routes
        java.util.Set<String> authTypes = new java.util.LinkedHashSet<>();
        for (RouteEntity route : routes) {
            if (route.getAuthTypes() != null) {
                authTypes.addAll(route.getAuthTypes());
            }
        }

        AuthPolicyRequest policy = new AuthPolicyRequest();
        policy.setAuthMode(entity.getAuthMode() != null ? entity.getAuthMode() : "ANY");
        policy.setAllowAnonymous(entity.getAllowAnonymous() != null && entity.getAllowAnonymous());
        policy.setEnabledAuthTypes(new java.util.ArrayList<>(authTypes));
        return policy;
    }

    @Transactional
    public ApiResponse updateAuthPolicy(UUID id, AuthPolicyRequest request) {
        ApiEntity entity = findApiOrThrow(id);

        if (request.getAuthMode() != null) {
            entity.setAuthMode(request.getAuthMode());
        }
        entity.setAllowAnonymous(request.isAllowAnonymous());

        ApiEntity saved = apiRepository.save(entity);

        // Propagate enabled auth types to ALL routes for this API
        List<RouteEntity> routes = routeRepository.findByApiId(id);
        if (request.getEnabledAuthTypes() != null && !request.getEnabledAuthTypes().isEmpty()) {
            for (RouteEntity route : routes) {
                route.setAuthTypes(request.getEnabledAuthTypes());
                routeRepository.save(route);
            }
            log.info("Updated auth_types on {} routes to: {}", routes.size(), request.getEnabledAuthTypes());
            // Refresh routes list after update
            routes = routeRepository.findByApiId(id);
            // Notify gateway to reload
            eventPublisher.publishConfigRefresh();
        }

        log.info("Auth policy updated for API: id={}, authMode={}, allowAnonymous={}, authTypes={}",
                id, saved.getAuthMode(), saved.getAllowAnonymous(), request.getEnabledAuthTypes());
        return toResponse(saved, routes);
    }

    @Transactional
    public ApiResponse updateGatewayConfig(UUID id, ApiGatewayConfigRequest request) {
        ApiEntity entity = findApiOrThrow(id);
        try {
            String configJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
            entity.setGatewayConfig(configJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize gateway config", e);
        }
        ApiEntity saved = apiRepository.save(entity);
        List<RouteEntity> routes = routeRepository.findByApiId(id);
        eventPublisher.publishConfigRefresh();
        log.info("Gateway config updated for API: id={}", id);
        return toResponse(saved, routes);
    }

    @Transactional(readOnly = true)
    public ApiGatewayConfigRequest getGatewayConfig(UUID id) {
        ApiEntity entity = findApiOrThrow(id);
        if (entity.getGatewayConfig() == null || entity.getGatewayConfig().isBlank()) {
            return new ApiGatewayConfigRequest();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(entity.getGatewayConfig(), ApiGatewayConfigRequest.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse gateway config for API {}: {}", id, e.getMessage());
            return new ApiGatewayConfigRequest();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String generateContextPath(String name) {
        if (name == null) return "api-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "api-" + UUID.randomUUID().toString().substring(0, 8);
        return slug;
    }

    private ApiEntity findApiOrThrow(UUID id) {
        return apiRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + id));
    }

    private void publishDomainEvent(String eventType, String resourceId) {
        String actorId = SecurityContextHelper.getCurrentUserId();
        eventPublisher.publish(
                RabbitMQExchanges.PLATFORM_EVENTS,
                eventType,
                new ApiDomainEvent(eventType, actorId, resourceId));
    }

    private ApiResponse toResponse(ApiEntity entity, List<RouteEntity> routes) {
        List<RouteResponse> routeResponses = routes != null
                ? routes.stream().map(this::toRouteResponse).toList()
                : Collections.emptyList();

        return ApiResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .contextPath(entity.getContextPath())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .visibility(entity.getVisibility())
                .protocolType(entity.getProtocolType())
                .tags(entity.getTags())
                .category(entity.getCategory())
                .authMode(entity.getAuthMode())
                .allowAnonymous(entity.getAllowAnonymous() != null && entity.getAllowAnonymous())
                .backendBaseUrl(entity.getBackendBaseUrl())
                .orgId(entity.getOrgId())
                .apiGroupId(entity.getApiGroupId())
                .apiGroupName(entity.getApiGroupName())
                .sensitivity(entity.getSensitivity())
                .versionStatus(entity.getVersionStatus())
                .deprecatedMessage(entity.getDeprecatedMessage())
                .successorVersionId(entity.getSuccessorVersionId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .routes(routeResponses)
                .build();
    }

    private RouteResponse toRouteResponse(RouteEntity route) {
        return RouteResponse.builder()
                .id(route.getId())
                .path(route.getPath())
                .method(route.getMethod())
                .upstreamUrl(route.getUpstreamUrl())
                .authTypes(route.getAuthTypes())
                .priority(route.getPriority() != null ? route.getPriority() : 0)
                .stripPrefix(route.getStripPrefix() != null && route.getStripPrefix())
                .enabled(route.getEnabled() != null && route.getEnabled())
                .requireMfa(route.getRequireMfa() != null && route.getRequireMfa())
                .createdAt(route.getCreatedAt())
                .build();
    }

    // ── Inner event class ────────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class ApiDomainEvent extends BaseEvent {
        private String resourceId;

        ApiDomainEvent(String eventType, String actorId, String resourceId) {
            super(eventType, actorId, null);
            this.resourceId = resourceId;
        }
    }
}
