package com.gateway.management.controller;

import com.gateway.management.entity.FederatedGatewayEntity;
import com.gateway.management.entity.WorkspaceEntity;
import com.gateway.management.service.FederationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/federation")
@RequiredArgsConstructor
public class FederationController {

    private final FederationService federationService;

    // ── Federated Gateways ────────────────────────────────────────────────

    @PostMapping("/gateways")
    public ResponseEntity<FederatedGatewayEntity> registerGateway(@RequestBody FederatedGatewayEntity request) {
        FederatedGatewayEntity result = federationService.registerGateway(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/gateways")
    public ResponseEntity<List<FederatedGatewayEntity>> listGateways() {
        return ResponseEntity.ok(federationService.listGateways());
    }

    @PostMapping("/gateways/{id}/sync")
    public ResponseEntity<List<Map<String, Object>>> syncApis(@PathVariable UUID id) {
        return ResponseEntity.ok(federationService.syncApis(id));
    }

    // ── Federated Catalog ─────────────────────────────────────────────────

    @GetMapping("/catalog")
    public ResponseEntity<List<Map<String, Object>>> getFederatedCatalog() {
        return ResponseEntity.ok(federationService.getFederatedCatalog());
    }

    // ── Workspaces ────────────────────────────────────────────────────────

    @PostMapping("/workspaces")
    public ResponseEntity<WorkspaceEntity> createWorkspace(@RequestBody WorkspaceEntity request) {
        WorkspaceEntity result = federationService.createWorkspace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<WorkspaceEntity>> listWorkspaces() {
        return ResponseEntity.ok(federationService.listWorkspaces());
    }
}
