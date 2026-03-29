package com.gateway.management.controller;

import com.gateway.management.dto.GatewayNodeResponse;
import com.gateway.management.service.ClusterManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/cluster")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterManagementService clusterManagementService;

    @GetMapping("/nodes")
    public ResponseEntity<List<GatewayNodeResponse>> listNodes() {
        return ResponseEntity.ok(clusterManagementService.listNodes());
    }

    @GetMapping("/nodes/{hostname}")
    public ResponseEntity<GatewayNodeResponse> getNode(@PathVariable String hostname) {
        return ResponseEntity.ok(clusterManagementService.getNode(hostname));
    }
}
