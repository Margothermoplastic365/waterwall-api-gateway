package com.gateway.management.controller;

import com.gateway.management.dto.CreateRouteRequest;
import com.gateway.management.dto.RouteResponse;
import com.gateway.management.dto.UpdateRouteRequest;
import com.gateway.management.service.RouteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteService routeService;

    @InjectMocks
    private RouteController routeController;

    @Test
    void createRoute_returnsCreated() {
        UUID apiId = UUID.randomUUID();
        CreateRouteRequest request = CreateRouteRequest.builder()
                .path("/pets")
                .method("GET")
                .upstreamUrl("http://backend:8080/pets")
                .build();
        RouteResponse expected = RouteResponse.builder()
                .id(UUID.randomUUID())
                .path("/pets")
                .method("GET")
                .upstreamUrl("http://backend:8080/pets")
                .build();
        when(routeService.createRoute(apiId, request)).thenReturn(expected);

        ResponseEntity<RouteResponse> response = routeController.createRoute(apiId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(routeService).createRoute(apiId, request);
    }

    @Test
    void listRoutes_returnsOk() {
        UUID apiId = UUID.randomUUID();
        List<RouteResponse> routes = List.of(
                RouteResponse.builder().id(UUID.randomUUID()).path("/pets").build(),
                RouteResponse.builder().id(UUID.randomUUID()).path("/owners").build()
        );
        when(routeService.listRoutes(apiId)).thenReturn(routes);

        ResponseEntity<List<RouteResponse>> response = routeController.listRoutes(apiId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(routeService).listRoutes(apiId);
    }

    @Test
    void updateRoute_returnsOk() {
        UUID apiId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UpdateRouteRequest request = new UpdateRouteRequest();
        request.setPath("/pets/updated");
        RouteResponse expected = RouteResponse.builder()
                .id(routeId)
                .path("/pets/updated")
                .build();
        when(routeService.updateRoute(apiId, routeId, request)).thenReturn(expected);

        ResponseEntity<RouteResponse> response = routeController.updateRoute(apiId, routeId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(routeService).updateRoute(apiId, routeId, request);
    }

    @Test
    void deleteRoute_returnsNoContent() {
        UUID apiId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();

        ResponseEntity<Void> response = routeController.deleteRoute(apiId, routeId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(routeService).deleteRoute(apiId, routeId);
    }
}
