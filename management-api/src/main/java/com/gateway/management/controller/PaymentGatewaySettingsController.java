package com.gateway.management.controller;

import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import com.gateway.management.service.PaymentGatewaySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payment-gateway-settings")
@RequiredArgsConstructor
public class PaymentGatewaySettingsController {

    private final PaymentGatewaySettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<PaymentGatewaySettingsEntity>> listAll() {
        return ResponseEntity.ok(settingsService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentGatewaySettingsEntity> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(settingsService.getById(id));
    }

    @GetMapping("/provider/{provider}")
    public ResponseEntity<PaymentGatewaySettingsEntity> getByProvider(@PathVariable String provider) {
        return ResponseEntity.ok(settingsService.getByProvider(provider));
    }

    @PostMapping
    public ResponseEntity<PaymentGatewaySettingsEntity> create(@RequestBody PaymentGatewaySettingsEntity entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settingsService.create(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentGatewaySettingsEntity> update(@PathVariable UUID id,
                                                                @RequestBody PaymentGatewaySettingsEntity entity) {
        return ResponseEntity.ok(settingsService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        settingsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PaymentGatewaySettingsEntity> toggleEnabled(@PathVariable UUID id) {
        return ResponseEntity.ok(settingsService.toggleEnabled(id));
    }
}
