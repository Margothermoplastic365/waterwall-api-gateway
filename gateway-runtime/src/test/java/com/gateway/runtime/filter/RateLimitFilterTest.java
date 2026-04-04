package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.cache.RateLimitCounter;
import com.gateway.common.events.EventPublisher;
import com.gateway.runtime.model.GatewayPlan;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.StrictRateLimitService;
import com.gateway.runtime.service.TokenBucketRateLimiter;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitCounter rateLimitCounter;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private StrictRateLimitService strictRateLimitService;

    @Mock
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final UUID apiId = UUID.randomUUID();
    private final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimitCounter, eventPublisher,
                strictRateLimitService, tokenBucketRateLimiter, objectMapper);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
        setUpMatchedRoute();
        setUpAuthentication();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassWhenNoPlan() throws Exception {
        // No ATTR_PLAN set on request

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassWhenEnforcementIsNone() throws Exception {
        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Free")
                .enforcement("NONE")
                .requestsPerMinute(100)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn429InStrictModeWhenLimitExceeded() throws Exception {
        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Gold")
                .enforcement("STRICT")
                .requestsPerMinute(10)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        when(strictRateLimitService.incrementAndCheck(anyString(), eq(60), eq(10)))
                .thenReturn(-1);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldPassInStrictModeWhenWithinLimit() throws Exception {
        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Gold")
                .enforcement("STRICT")
                .requestsPerMinute(10)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        when(strictRateLimitService.incrementAndCheck(anyString(), eq(60), eq(10)))
                .thenReturn(5);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetRateLimitHeaders() throws Exception {
        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Gold")
                .enforcement("STRICT")
                .requestsPerMinute(100)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        when(strictRateLimitService.incrementAndCheck(anyString(), eq(60), eq(100)))
                .thenReturn(25);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("75");
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
        verify(filterChain).doFilter(request, response);
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

    private void setUpAuthentication() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user-1", "org-1", "user@test.com",
                List.of("USER"), List.of(), appId.toString(), "read"
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
