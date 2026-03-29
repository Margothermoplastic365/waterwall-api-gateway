package com.gateway.management.service;

import com.gateway.common.events.EventPublisher;
import com.gateway.management.dto.CreateRouteRequest;
import com.gateway.management.dto.RouteResponse;
import com.gateway.management.dto.UpdateRouteRequest;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final ApiRepository apiRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public RouteResponse createRoute(UUID apiId, CreateRouteRequest request) {
        ApiEntity api = findApiOrThrow(apiId);

        RouteEntity entity = RouteEntity.builder()
                .api(api)
                .path(request.getPath())
                .method(request.getMethod())
                .upstreamUrl(request.getUpstreamUrl())
                .authTypes(request.getAuthTypes())
                .priority(request.getPriority())
                .stripPrefix(request.isStripPrefix())
                .enabled(true)
                .build();

        RouteEntity saved = routeRepository.save(entity);
        log.info("Route created: id={}, apiId={}, path={}", saved.getId(), apiId, saved.getPath());

        publishConfigRefresh();
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(UUID apiId) {
        findApiOrThrow(apiId);
        return routeRepository.findByApiId(apiId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RouteResponse updateRoute(UUID apiId, UUID routeId, UpdateRouteRequest request) {
        findApiOrThrow(apiId);
        RouteEntity entity = findRouteOrThrow(routeId, apiId);

        if (request.getPath() != null) {
            entity.setPath(request.getPath());
        }
        if (request.getMethod() != null) {
            entity.setMethod(request.getMethod());
        }
        if (request.getUpstreamUrl() != null) {
            entity.setUpstreamUrl(request.getUpstreamUrl());
        }
        if (request.getAuthTypes() != null) {
            entity.setAuthTypes(request.getAuthTypes());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        if (request.getStripPrefix() != null) {
            entity.setStripPrefix(request.getStripPrefix());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }

        RouteEntity saved = routeRepository.save(entity);
        log.info("Route updated: id={}, apiId={}", saved.getId(), apiId);

        publishConfigRefresh();
        return toResponse(saved);
    }

    @Transactional
    public void deleteRoute(UUID apiId, UUID routeId) {
        findApiOrThrow(apiId);
        RouteEntity entity = findRouteOrThrow(routeId, apiId);
        routeRepository.delete(entity);
        log.info("Route deleted: id={}, apiId={}", routeId, apiId);

        publishConfigRefresh();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ApiEntity findApiOrThrow(UUID apiId) {
        return apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));
    }

    private RouteEntity findRouteOrThrow(UUID routeId, UUID apiId) {
        RouteEntity route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route not found: " + routeId));
        if (!route.getApi().getId().equals(apiId)) {
            throw new EntityNotFoundException(
                    "Route " + routeId + " does not belong to API " + apiId);
        }
        return route;
    }

    private void publishConfigRefresh() {
        eventPublisher.publishConfigRefresh();
    }

    private RouteResponse toResponse(RouteEntity entity) {
        return RouteResponse.builder()
                .id(entity.getId())
                .path(entity.getPath())
                .method(entity.getMethod())
                .upstreamUrl(entity.getUpstreamUrl())
                .authTypes(entity.getAuthTypes())
                .priority(entity.getPriority() != null ? entity.getPriority() : 0)
                .stripPrefix(entity.getStripPrefix() != null && entity.getStripPrefix())
                .enabled(entity.getEnabled() != null && entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
