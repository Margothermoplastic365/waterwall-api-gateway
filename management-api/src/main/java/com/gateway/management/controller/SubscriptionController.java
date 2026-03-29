package com.gateway.management.controller;

import com.gateway.management.dto.CreateSubscriptionRequest;
import com.gateway.management.dto.ReviewSubscriptionRequest;
import com.gateway.management.dto.SubscriptionResponse;
import com.gateway.management.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse response = subscriptionService.subscribe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> listSubscriptions(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID apiId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(subscriptionService.listSubscriptions(applicationId, apiId, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unsubscribe(@PathVariable UUID id) {
        subscriptionService.unsubscribe(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<SubscriptionResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewSubscriptionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(subscriptionService.approveSubscription(id, reason));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<SubscriptionResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewSubscriptionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(subscriptionService.rejectSubscription(id, reason));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<SubscriptionResponse> suspend(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewSubscriptionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(subscriptionService.suspendSubscription(id, reason));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resume(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewSubscriptionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(subscriptionService.resumeSubscription(id, reason));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<SubscriptionResponse> revoke(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewSubscriptionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(subscriptionService.revokeSubscription(id, reason));
    }
}
