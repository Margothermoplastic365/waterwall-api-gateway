package com.gateway.runtime.proxy;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolDispatcherTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void shouldDispatchToCorrectHandler() {
        ProtocolProxyHandler restHandler = mock(ProtocolProxyHandler.class);
        when(restHandler.getProtocolType()).thenReturn("REST");

        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok("ok".getBytes());
        MatchedRoute matchedRoute = buildMatchedRoute("REST");
        when(restHandler.proxy(request, matchedRoute)).thenReturn(expectedResponse);

        ProtocolDispatcher dispatcher = new ProtocolDispatcher(List.of(restHandler));

        ResponseEntity<byte[]> result = dispatcher.dispatch("REST", request, matchedRoute);

        assertThat(result).isSameAs(expectedResponse);
        verify(restHandler).proxy(request, matchedRoute);
    }

    @Test
    void shouldReturn501ForUnsupportedProtocol() {
        ProtocolProxyHandler restHandler = mock(ProtocolProxyHandler.class);
        when(restHandler.getProtocolType()).thenReturn("REST");

        ProtocolDispatcher dispatcher = new ProtocolDispatcher(List.of(restHandler));
        MatchedRoute matchedRoute = buildMatchedRoute("UNKNOWN");

        ResponseEntity<byte[]> result = dispatcher.dispatch("UNKNOWN", request, matchedRoute);

        assertThat(result.getStatusCode().value()).isEqualTo(501);
        assertThat(new String(result.getBody())).contains("not_implemented");
    }

    @Test
    void shouldIndexHandlersByProtocolType() {
        ProtocolProxyHandler restHandler = mock(ProtocolProxyHandler.class);
        when(restHandler.getProtocolType()).thenReturn("REST");

        ProtocolProxyHandler soapHandler = mock(ProtocolProxyHandler.class);
        when(soapHandler.getProtocolType()).thenReturn("SOAP");

        MatchedRoute restRoute = buildMatchedRoute("REST");
        MatchedRoute soapRoute = buildMatchedRoute("SOAP");

        ResponseEntity<byte[]> restResponse = ResponseEntity.ok("rest".getBytes());
        ResponseEntity<byte[]> soapResponse = ResponseEntity.ok("soap".getBytes());

        when(restHandler.proxy(request, restRoute)).thenReturn(restResponse);
        when(soapHandler.proxy(request, soapRoute)).thenReturn(soapResponse);

        ProtocolDispatcher dispatcher = new ProtocolDispatcher(List.of(restHandler, soapHandler));

        assertThat(dispatcher.dispatch("REST", request, restRoute)).isSameAs(restResponse);
        assertThat(dispatcher.dispatch("SOAP", request, soapRoute)).isSameAs(soapResponse);
    }

    @Test
    void shouldDefaultToRestWhenProtocolTypeNull() {
        ProtocolProxyHandler restHandler = mock(ProtocolProxyHandler.class);
        when(restHandler.getProtocolType()).thenReturn("REST");

        MatchedRoute matchedRoute = buildMatchedRoute(null);
        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok("default".getBytes());
        when(restHandler.proxy(request, matchedRoute)).thenReturn(expectedResponse);

        ProtocolDispatcher dispatcher = new ProtocolDispatcher(List.of(restHandler));

        ResponseEntity<byte[]> result = dispatcher.dispatch(null, request, matchedRoute);

        assertThat(result).isSameAs(expectedResponse);
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
