package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.ApiDeploymentRequest;
import com.gateway.management.dto.ApiDeploymentResponse;
import com.gateway.management.dto.EnvironmentResponse;
import com.gateway.management.service.EnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @GetMapping
    public ResponseEntity<List<EnvironmentResponse>> listEnvironments() {
        return ResponseEntity.ok(environmentService.listEnvironments());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<EnvironmentResponse> getEnvironment(@PathVariable String slug) {
        return ResponseEntity.ok(environmentService.getEnvironment(slug));
    }

    @PostMapping("/deploy")
    @RequiresPermission("environment:deploy")
    public ResponseEntity<ApiDeploymentResponse> deployApi(
            @Valid @RequestBody ApiDeploymentRequest request) {
        ApiDeploymentResponse response = environmentService.deployApi(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/deployments")
    public ResponseEntity<List<ApiDeploymentResponse>> getDeploymentsForApi(
            @RequestParam UUID apiId) {
        return ResponseEntity.ok(environmentService.getDeploymentsForApi(apiId));
    }
}
