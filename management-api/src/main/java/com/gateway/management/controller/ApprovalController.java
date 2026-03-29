package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.ApprovalActionRequest;
import com.gateway.management.dto.ApprovalResponse;
import com.gateway.management.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    public ResponseEntity<ApprovalResponse> requestApproval(
            @RequestBody ApprovalActionRequest request) {
        ApprovalResponse response = approvalService.requestApproval(
                request.getType(), request.getResourceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission("subscription:approve")
    public ResponseEntity<ApprovalResponse> approve(@PathVariable UUID id) {
        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID approverId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(approvalService.approve(id, approverId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable UUID id,
            @RequestBody ApprovalActionRequest request) {
        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID rejecterId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(approvalService.reject(id, rejecterId, request.getReason()));
    }

    @GetMapping
    public ResponseEntity<List<ApprovalResponse>> listAll() {
        return ResponseEntity.ok(approvalService.listAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalResponse>> listPending() {
        return ResponseEntity.ok(approvalService.listPending());
    }
}
