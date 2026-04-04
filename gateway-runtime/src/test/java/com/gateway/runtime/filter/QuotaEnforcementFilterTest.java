package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.runtime.model.GatewayPlan;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcementFilterTest {

    @Mock
    private JdbcTemplate gatewayJdbcTemplate;

    @Mock
    private FilterChain filterChain;

    private QuotaEnforcementFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new QuotaEnforcementFilter(objectMapper, gatewayJdbcTemplate);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
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
    void shouldPassWhenNoMonthlyQuota() throws Exception {
        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Basic")
                .enforcement("STRICT")
                .maxRequestsPerMonth(null)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn429WhenQuotaExceededInStrictMode() throws Exception {
        setUpAuthentication();

        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Gold")
                .enforcement("STRICT")
                .maxRequestsPerMonth(5L)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        // Make requests to exhaust the quota
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request, resp, filterChain);
        }

        // The 6th request should be rejected
        MockHttpServletResponse finalResponse = new MockHttpServletResponse();
        filter.doFilter(request, finalResponse, filterChain);

        assertThat(finalResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldSetQuotaHeaders() throws Exception {
        setUpAuthentication();

        GatewayPlan plan = GatewayPlan.builder()
                .planId(UUID.randomUUID())
                .name("Gold")
                .enforcement("STRICT")
                .maxRequestsPerMonth(1000L)
                .build();
        request.setAttribute(SubscriptionCheckFilter.ATTR_PLAN, plan);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Quota-Limit")).isEqualTo("1000");
        assertThat(response.getHeader("X-Quota-Used")).isNotNull();
        assertThat(response.getHeader("X-Quota-Remaining")).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    private void setUpAuthentication() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user-1", "org-1", "user@test.com",
                List.of("USER"), List.of(), appId.toString(), "read"
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
