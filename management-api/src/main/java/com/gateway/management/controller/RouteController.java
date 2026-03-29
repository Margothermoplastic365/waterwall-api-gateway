package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.CreateRouteRequest;
import com.gateway.management.dto.RouteResponse;
import com.gateway.management.dto.UpdateRouteRequest;
import com.gateway.management.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/apis/{apiId}/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @RequiresPermission("api:update")
    public ResponseEntity<RouteResponse> createRoute(@PathVariable UUID apiId,
                                                      @Valid @RequestBody CreateRouteRequest request) {
        RouteResponse response = routeService.createRoute(apiId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RouteResponse>> listRoutes(@PathVariable UUID apiId) {
        return ResponseEntity.ok(routeService.listRoutes(apiId));
    }

    @PutMapping("/{routeId}")
    @RequiresPermission("api:update")
    public ResponseEntity<RouteResponse> updateRoute(@PathVariable UUID apiId,
                                                      @PathVariable UUID routeId,
                                                      @Valid @RequestBody UpdateRouteRequest request) {
        return ResponseEntity.ok(routeService.updateRoute(apiId, routeId, request));
    }

    @DeleteMapping("/{routeId}")
    @RequiresPermission("api:update")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID apiId,
                                             @PathVariable UUID routeId) {
        routeService.deleteRoute(apiId, routeId);
        return ResponseEntity.noContent().build();
    }
}
