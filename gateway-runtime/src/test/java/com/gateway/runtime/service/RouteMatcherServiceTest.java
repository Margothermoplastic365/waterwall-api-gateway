package com.gateway.runtime.service;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteMatcherServiceTest {

    @Mock
    private RouteConfigService routeConfigService;

    private RouteMatcherService routeMatcherService;

    @BeforeEach
    void setUp() {
        routeMatcherService = new RouteMatcherService(routeConfigService);
    }

    @Test
    void shouldMatchExactPath() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users")
                .method("GET")
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(route));

        MatchedRoute result = routeMatcherService.match("/api/users", "GET");

        assertThat(result).isNotNull();
        assertThat(result.getRoute().getPath()).isEqualTo("/api/users");
        assertThat(result.getPathVariables()).isEmpty();
    }

    @Test
    void shouldMatchPathWithVariable() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users/{id}")
                .method("GET")
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(route));

        MatchedRoute result = routeMatcherService.match("/api/users/123", "GET");

        assertThat(result).isNotNull();
        assertThat(result.getRoute().getPath()).isEqualTo("/api/users/{id}");
        assertThat(result.getPathVariables()).containsEntry("id", "123");
    }

    @Test
    void shouldReturnNullWhenNoMatch() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users")
                .method("GET")
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(route));

        MatchedRoute result = routeMatcherService.match("/api/orders", "GET");

        assertThat(result).isNull();
    }

    @Test
    void shouldMatchByMethod() {
        GatewayRoute getRoute = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users")
                .method("GET")
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(getRoute));

        // GET matches
        MatchedRoute getResult = routeMatcherService.match("/api/users", "GET");
        assertThat(getResult).isNotNull();

        // POST does not match
        MatchedRoute postResult = routeMatcherService.match("/api/users", "POST");
        assertThat(postResult).isNull();
    }

    @Test
    void shouldPreferHigherPriority() {
        GatewayRoute lowPriority = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users/**")
                .upstreamUrl("http://low-priority:8080")
                .priority(1)
                .enabled(true)
                .build();
        GatewayRoute highPriority = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users/**")
                .upstreamUrl("http://high-priority:8080")
                .priority(10)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(lowPriority, highPriority));

        MatchedRoute result = routeMatcherService.match("/api/users/123", "GET");

        assertThat(result).isNotNull();
        assertThat(result.getRoute().getUpstreamUrl()).isEqualTo("http://high-priority:8080");
    }

    @Test
    void shouldNotMatchDisabledRoute() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users")
                .method("GET")
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(false)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(route));

        MatchedRoute result = routeMatcherService.match("/api/users", "GET");

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenNoRoutesExist() {
        when(routeConfigService.getAllRoutes()).thenReturn(Collections.emptyList());

        MatchedRoute result = routeMatcherService.match("/api/anything", "GET");

        assertThat(result).isNull();
    }

    @Test
    void shouldMatchRouteWithNullMethod() {
        // null method on route means any HTTP method should match
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .path("/api/users")
                .method(null)
                .upstreamUrl("http://users-service:8080")
                .priority(1)
                .enabled(true)
                .build();
        when(routeConfigService.getAllRoutes()).thenReturn(List.of(route));

        MatchedRoute result = routeMatcherService.match("/api/users", "DELETE");

        assertThat(result).isNotNull();
    }
}
