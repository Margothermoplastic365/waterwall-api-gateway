package com.gateway.runtime.controller;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.proxy.ProtocolDispatcher;
import com.gateway.runtime.transform.BodyTransformer;
import com.gateway.runtime.transform.HeaderTransformer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock
    private ProtocolDispatcher protocolDispatcher;

    @Mock
    private HeaderTransformer headerTransformer;

    @Mock
    private BodyTransformer bodyTransformer;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private ProxyController proxyController;

    @Test
    void shouldDelegateToProtocolDispatcher() {
        MatchedRoute matchedRoute = buildMatchedRoute("REST");
        when(request.getAttribute("gateway.matchedRoute")).thenReturn(matchedRoute);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok("response".getBytes());
        when(protocolDispatcher.dispatch(eq("REST"), eq(request), eq(matchedRoute)))
                .thenReturn(expectedResponse);

        Object result = proxyController.proxy(request, response);

        verify(protocolDispatcher).dispatch("REST", request, matchedRoute);
        assertThat(result).isInstanceOf(ResponseEntity.class);
    }

    @Test
    void shouldReturn404WhenNoMatchedRoute() {
        when(request.getAttribute("gateway.matchedRoute")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/unknown");

        Object result = proxyController.proxy(request, response);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<byte[]> responseEntity = (ResponseEntity<byte[]>) result;
        assertThat(responseEntity.getStatusCode().value()).isEqualTo(404);
        assertThat(new String(responseEntity.getBody())).contains("no_route");
        verifyNoInteractions(protocolDispatcher);
    }

    @Test
    void shouldDefaultToRestWhenProtocolTypeBlank() {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .apiName("test")
                .path("/api/test")
                .method("GET")
                .upstreamUrl("http://backend:8080")
                .protocolType("")
                .enabled(true)
                .build();
        MatchedRoute matchedRoute = MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();

        when(request.getAttribute("gateway.matchedRoute")).thenReturn(matchedRoute);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok("ok".getBytes());
        when(protocolDispatcher.dispatch(eq("REST"), eq(request), eq(matchedRoute)))
                .thenReturn(expectedResponse);

        proxyController.proxy(request, response);

        verify(protocolDispatcher).dispatch("REST", request, matchedRoute);
    }

    @Test
    void shouldReturn426ForWebSocketProtocol() {
        MatchedRoute matchedRoute = buildMatchedRoute("WEBSOCKET");
        when(request.getAttribute("gateway.matchedRoute")).thenReturn(matchedRoute);

        Object result = proxyController.proxy(request, response);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<byte[]> responseEntity = (ResponseEntity<byte[]>) result;
        assertThat(responseEntity.getStatusCode().value()).isEqualTo(426);
        assertThat(new String(responseEntity.getBody())).contains("upgrade_required");
        verifyNoInteractions(protocolDispatcher);
    }

    private MatchedRoute buildMatchedRoute(String protocolType) {
        GatewayRoute route = GatewayRoute.builder()
                .routeId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .apiName("test")
                .path("/api/test")
                .method("GET")
                .upstreamUrl("http://backend:8080")
                .protocolType(protocolType)
                .enabled(true)
                .build();
        return MatchedRoute.builder()
                .route(route)
                .pathVariables(Map.of())
                .build();
    }
}
