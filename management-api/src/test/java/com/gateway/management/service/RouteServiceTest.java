package com.gateway.management.service;

import com.gateway.common.events.EventPublisher;
import com.gateway.management.dto.CreateRouteRequest;
import com.gateway.management.dto.RouteResponse;
import com.gateway.management.dto.UpdateRouteRequest;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private RouteService routeService;

    private ApiEntity sampleApi() {
        return ApiEntity.builder()
                .id(UUID.randomUUID())
                .name("Test API")
                .status(ApiStatus.CREATED)
                .build();
    }

    @Test
    void shouldCreateRoute() {
        ApiEntity api = sampleApi();
        UUID apiId = api.getId();
        CreateRouteRequest request = CreateRouteRequest.builder()
                .path("/users")
                .method("GET")
                .upstreamUrl("http://backend/users")
                .authTypes(List.of("API_KEY"))
                .priority(1)
                .stripPrefix(true)
                .build();

        RouteEntity savedRoute = RouteEntity.builder()
                .id(UUID.randomUUID())
                .api(api)
                .path("/users")
                .method("GET")
                .upstreamUrl("http://backend/users")
                .authTypes(List.of("API_KEY"))
                .priority(1)
                .stripPrefix(true)
                .enabled(true)
                .build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(routeRepository.save(any(RouteEntity.class))).thenReturn(savedRoute);

        RouteResponse response = routeService.createRoute(apiId, request);

        assertThat(response.getPath()).isEqualTo("/users");
        assertThat(response.getMethod()).isEqualTo("GET");
        assertThat(response.getUpstreamUrl()).isEqualTo("http://backend/users");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isStripPrefix()).isTrue();

        verify(routeRepository).save(any(RouteEntity.class));
        verify(eventPublisher).publishConfigRefresh();
    }

    @Test
    void shouldListRoutesForApi() {
        ApiEntity api = sampleApi();
        UUID apiId = api.getId();

        RouteEntity route1 = RouteEntity.builder()
                .id(UUID.randomUUID()).api(api).path("/a").method("GET")
                .upstreamUrl("http://b/a").enabled(true).priority(0).stripPrefix(false).build();
        RouteEntity route2 = RouteEntity.builder()
                .id(UUID.randomUUID()).api(api).path("/b").method("POST")
                .upstreamUrl("http://b/b").enabled(true).priority(1).stripPrefix(false).build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(routeRepository.findByApiId(apiId)).thenReturn(List.of(route1, route2));

        List<RouteResponse> routes = routeService.listRoutes(apiId);

        assertThat(routes).hasSize(2);
        assertThat(routes).extracting(RouteResponse::getPath)
                .containsExactly("/a", "/b");
    }

    @Test
    void shouldUpdateRoute() {
        ApiEntity api = sampleApi();
        UUID apiId = api.getId();
        UUID routeId = UUID.randomUUID();

        RouteEntity existing = RouteEntity.builder()
                .id(routeId).api(api).path("/old").method("GET")
                .upstreamUrl("http://old").enabled(true).priority(0).stripPrefix(false).build();

        UpdateRouteRequest request = new UpdateRouteRequest();
        request.setPath("/new");
        request.setMethod("POST");
        // upstreamUrl, enabled etc. left null -> should not be updated

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(existing));
        when(routeRepository.save(any(RouteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RouteResponse response = routeService.updateRoute(apiId, routeId, request);

        assertThat(response.getPath()).isEqualTo("/new");
        assertThat(response.getMethod()).isEqualTo("POST");
        assertThat(response.getUpstreamUrl()).isEqualTo("http://old");
        verify(eventPublisher).publishConfigRefresh();
    }

    @Test
    void shouldDeleteRoute() {
        ApiEntity api = sampleApi();
        UUID apiId = api.getId();
        UUID routeId = UUID.randomUUID();

        RouteEntity existing = RouteEntity.builder()
                .id(routeId).api(api).path("/delete-me").method("DELETE")
                .upstreamUrl("http://x").enabled(true).priority(0).stripPrefix(false).build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(existing));

        routeService.deleteRoute(apiId, routeId);

        verify(routeRepository).delete(existing);
        verify(eventPublisher).publishConfigRefresh();
    }
}
