package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockModeFilterTest {

    @Mock
    private JdbcTemplate gatewayJdbcTemplate;

    @Mock
    private FilterChain filterChain;

    private MockModeFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final UUID apiId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new MockModeFilter(gatewayJdbcTemplate, objectMapper);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/pets");
        request.setMethod("GET");
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldPassThroughWhenNotMockMode() throws Exception {
        setUpMatchedRoute();

        // No X-Mock-Mode header and DB returns no mock config
        when(gatewayJdbcTemplate.queryForObject(
                contains("mock_configs"), eq(Integer.class), any(UUID.class)))
                .thenReturn(0);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnMockResponseWhenMockModeEnabled() throws Exception {
        setUpMatchedRoute();
        request.addHeader("X-Mock-Mode", "true");

        // Mock the query that finds mock config for the API/path/method
        // The filter calls gatewayJdbcTemplate.query(...) with RowMapper
        when(gatewayJdbcTemplate.query(contains("mock_configs"), any(RowMapper.class),
                any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    // Create a mock ResultSet scenario - return empty to trigger default mock response
                    return Collections.emptyList();
                });

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Mock")).isEqualTo("true");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Mock response");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldSetMockModeAttribute() throws Exception {
        setUpMatchedRoute();
        request.addHeader("X-Mock-Mode", "true");

        // Return empty list from mock config query to use default mock response
        when(gatewayJdbcTemplate.query(contains("mock_configs"), any(RowMapper.class),
                any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(MockModeFilter.ATTR_MOCK_MODE)).isEqualTo(true);
    }

    private void setUpMatchedRoute() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(apiId)
                .path("/api/pets")
                .method("GET")
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();
        request.setAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE, matchedRoute);
    }
}
