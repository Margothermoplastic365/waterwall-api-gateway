package com.gateway.management.controller;

import com.gateway.management.dto.MockConfigRequest;
import com.gateway.management.dto.MockConfigResponse;
import com.gateway.management.dto.MockResponse;
import com.gateway.management.service.MockingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/mocking")
@RequiredArgsConstructor
public class MockController {

    private final MockingService mockingService;

    @PostMapping("/{apiId}/enable")
    public ResponseEntity<Map<String, Object>> enableMockMode(@PathVariable UUID apiId) {
        mockingService.setMockMode(apiId, true);
        return ResponseEntity.ok(Map.of("apiId", apiId, "mockEnabled", true));
    }

    @PostMapping("/{apiId}/disable")
    public ResponseEntity<Map<String, Object>> disableMockMode(@PathVariable UUID apiId) {
        mockingService.setMockMode(apiId, false);
        return ResponseEntity.ok(Map.of("apiId", apiId, "mockEnabled", false));
    }

    @GetMapping("/{apiId}/configs")
    public ResponseEntity<List<MockConfigResponse>> getConfigs(@PathVariable UUID apiId) {
        return ResponseEntity.ok(mockingService.getConfigs(apiId));
    }

    @PostMapping("/{apiId}/configs")
    public ResponseEntity<MockConfigResponse> addConfig(@PathVariable UUID apiId,
                                                         @RequestBody MockConfigRequest request) {
        MockConfigResponse response = mockingService.addConfig(apiId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{apiId}/configs/{configId}")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID apiId, @PathVariable UUID configId) {
        mockingService.deleteConfig(apiId, configId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiId}/generate")
    public ResponseEntity<MockResponse> generateMock(@PathVariable UUID apiId,
                                                      @RequestParam(defaultValue = "/**") String path,
                                                      @RequestParam(defaultValue = "GET") String method) {
        MockResponse response = mockingService.generateMock(apiId, path, method);
        return ResponseEntity.ok(response);
    }
}
