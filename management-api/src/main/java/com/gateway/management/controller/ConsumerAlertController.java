package com.gateway.management.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.entity.ConsumerAlertRuleEntity;
import com.gateway.management.service.ConsumerAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/consumer/alerts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConsumerAlertController {

    private final ConsumerAlertService consumerAlertService;

    /**
     * Creates a new alert rule for the authenticated consumer.
     */
    @PostMapping
    public ResponseEntity<ConsumerAlertRuleEntity> createRule(@RequestBody Map<String, String> request) {
        UUID userId = resolveUserId();
        ConsumerAlertRuleEntity rule = consumerAlertService.createRule(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * Lists all alert rules for the authenticated consumer.
     */
    @GetMapping
    public ResponseEntity<List<ConsumerAlertRuleEntity>> listRules() {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(consumerAlertService.listRules(userId));
    }

    /**
     * Updates an existing alert rule for the authenticated consumer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ConsumerAlertRuleEntity> updateRule(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(consumerAlertService.updateRule(userId, id, request));
    }

    /**
     * Deletes an alert rule for the authenticated consumer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        UUID userId = resolveUserId();
        consumerAlertService.deleteRule(userId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId() {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated consumer found in security context");
        }
        return UUID.fromString(userId);
    }
}
