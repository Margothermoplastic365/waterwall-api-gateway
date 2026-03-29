package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.common.dto.PageResponse;
import com.gateway.management.dto.ApiResponse;
import com.gateway.management.dto.AuthPolicyRequest;
import com.gateway.management.dto.ApiGatewayConfigRequest;
import com.gateway.management.dto.CreateApiRequest;
import com.gateway.management.dto.UpdateApiRequest;
import com.gateway.management.service.ApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @PostMapping
    @RequiresPermission("api:create")
    public ResponseEntity<ApiResponse> createApi(@Valid @RequestBody CreateApiRequest request) {
        ApiResponse response = apiService.createApi(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ApiResponse>> listApis(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ApiResponse> result = apiService.listApis(search, status, category, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getApi(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.getApi(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission("api:update")
    public ResponseEntity<ApiResponse> updateApi(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateApiRequest request) {
        return ResponseEntity.ok(apiService.updateApi(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("api:delete")
    public ResponseEntity<Void> deleteApi(@PathVariable UUID id) {
        apiService.deleteApi(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("api:publish")
    public ResponseEntity<ApiResponse> publishApi(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.publishApi(id));
    }

    @PostMapping("/{id}/deprecate")
    @RequiresPermission("api:deprecate")
    public ResponseEntity<ApiResponse> deprecateApi(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.deprecateApi(id));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("api:retire")
    public ResponseEntity<ApiResponse> retireApi(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.retireApi(id));
    }

    @GetMapping("/{id}/auth-policy")
    public ResponseEntity<AuthPolicyRequest> getAuthPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.getAuthPolicy(id));
    }

    @PutMapping("/{id}/auth-policy")
    @RequiresPermission("api:update")
    public ResponseEntity<ApiResponse> updateAuthPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody AuthPolicyRequest request) {
        return ResponseEntity.ok(apiService.updateAuthPolicy(id, request));
    }

    @PutMapping("/{id}/gateway-config")
    @RequiresPermission("api:update")
    public ResponseEntity<ApiResponse> updateGatewayConfig(
            @PathVariable UUID id,
            @RequestBody ApiGatewayConfigRequest request) {
        return ResponseEntity.ok(apiService.updateGatewayConfig(id, request));
    }

    @GetMapping("/{id}/gateway-config")
    public ResponseEntity<ApiGatewayConfigRequest> getGatewayConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(apiService.getGatewayConfig(id));
    }
}
