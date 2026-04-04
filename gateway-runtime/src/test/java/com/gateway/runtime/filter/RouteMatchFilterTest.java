package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.RouteMatcherService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteMatchFilterTest {

    @Mock
    private RouteMatcherService routeMatcherService;

    @Mock
    private FilterChain filterChain;

    private RouteMatchFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        filter = new RouteMatchFilter(routeMatcherService, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldStoreMatchedRouteAsAttribute() throws Exception {
        request.setRequestURI("/api/pets");
        request.setMethod("GET");

        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .path("/api/pets")
                .method("GET")
                .upstreamUrl("http://localhost:8080/pets")
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();

        when(routeMatcherService.match("/api/pets", "GET")).thenReturn(matchedRoute);

        // Set auth type so it doesn't fail auth check
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "ANONYMOUS");

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE)).isEqualTo(matchedRoute);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn404WhenNoRouteMatches() throws Exception {
        request.setRequestURI("/api/unknown");
        request.setMethod("GET");

        when(routeMatcherService.match("/api/unknown", "GET")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldSkipInternalManagementPaths() throws Exception {
        request.setRequestURI("/v1/gateway/routes");
        request.setMethod("GET");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeMatcherService);
    }

    @Test
    void shouldSkipActuatorEndpoints() throws Exception {
        request.setRequestURI("/actuator/health");
        request.setMethod("GET");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeMatcherService);
    }

    @Test
    void shouldReturn401WhenAuthRequiredButAnonymous() throws Exception {
        request.setRequestURI("/api/secure");
        request.setMethod("GET");

        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .path("/api/secure")
                .method("GET")
                .authTypes(List.of("API_KEY"))
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();

        when(routeMatcherService.match("/api/secure", "GET")).thenReturn(matchedRoute);
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "ANONYMOUS");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldPassWhenAuthTypeMatches() throws Exception {
        request.setRequestURI("/api/secure");
        request.setMethod("GET");

        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .path("/api/secure")
                .method("GET")
                .authTypes(List.of("API_KEY"))
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();

        when(routeMatcherService.match("/api/secure", "GET")).thenReturn(matchedRoute);
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "API_KEY");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }
}
