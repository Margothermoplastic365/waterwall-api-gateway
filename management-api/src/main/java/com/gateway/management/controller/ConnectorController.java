package com.gateway.management.controller;

import com.gateway.management.dto.ConnectorResponse;
import com.gateway.management.dto.CreateConnectorRequest;
import com.gateway.management.service.ConnectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;

    @PostMapping
    public ResponseEntity<ConnectorResponse> create(@Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectorService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ConnectorResponse>> listAll() {
        return ResponseEntity.ok(connectorService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConnectorResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConnectorResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.ok(connectorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        connectorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectorResponse> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.testConnection(id));
    }
}
