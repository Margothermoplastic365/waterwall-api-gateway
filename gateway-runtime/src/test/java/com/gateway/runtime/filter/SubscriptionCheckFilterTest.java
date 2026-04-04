package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.runtime.model.GatewayPlan;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.GatewaySubscription;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.RouteConfigService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckFilterTest {

    @Mock
    private RouteConfigService routeConfigService;

    @Mock
    private FilterChain filterChain;

    private SubscriptionCheckFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final UUID apiId = UUID.randomUUID();
    private final UUID appId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new SubscriptionCheckFilter(routeConfigService, objectMapper);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughForAnonymousAuth() throws Exception {
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "ANONYMOUS");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeConfigService);
    }

    @Test
    void shouldReturn403WhenNoSubscription() throws Exception {
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "API_KEY");
        setUpAuthentication();
        setUpMatchedRoute();

        when(routeConfigService.getSubscription(eq(appId), eq(apiId), any()))
                .thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldStoreSubscriptionAndPlanAttributes() throws Exception {
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "API_KEY");
        setUpAuthentication();
        setUpMatchedRoute();

        GatewaySubscription subscription = GatewaySubscription.builder()
                .subscriptionId(UUID.randomUUID())
                .applicationId(appId)
                .apiId(apiId)
                .planId(planId)
                .status("ACTIVE")
                .build();

        GatewayPlan plan = GatewayPlan.builder()
                .planId(planId)
                .name("Gold")
                .enforcement("STRICT")
                .requestsPerMinute(100)
                .build();

        when(routeConfigService.getSubscription(eq(appId), eq(apiId), any()))
                .thenReturn(Optional.of(subscription));
        when(routeConfigService.getPlansById())
                .thenReturn(Map.of(planId, plan));

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SubscriptionCheckFilter.ATTR_SUBSCRIPTION)).isEqualTo(subscription);
        assertThat(request.getAttribute(SubscriptionCheckFilter.ATTR_PLAN)).isEqualTo(plan);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn401WhenNoAuthentication() throws Exception {
        request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, "API_KEY");
        // No authentication set in SecurityContext

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    private void setUpAuthentication() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user-1", "org-1", "user@test.com",
                List.of("USER"), List.of(), appId.toString(), "read"
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUpMatchedRoute() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(apiId)
                .path("/api/test")
                .method("GET")
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();
        request.setAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE, matchedRoute);
    }
}
