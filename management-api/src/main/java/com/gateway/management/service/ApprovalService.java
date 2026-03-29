package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.ApprovalResponse;
import com.gateway.management.entity.ApprovalRequestEntity;
import com.gateway.management.repository.ApprovalRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;

    @Transactional
    public ApprovalResponse requestApproval(String type, UUID resourceId) {
        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID requestedBy = currentUserId != null ? UUID.fromString(currentUserId) : null;

        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .type(type)
                .resourceId(resourceId)
                .status("PENDING")
                .requestedBy(requestedBy)
                .requestedAt(Instant.now())
                .build();

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        log.info("Approval requested: id={}, type={}, resourceId={}", saved.getId(), type, resourceId);
        return toResponse(saved);
    }

    @Transactional
    public ApprovalResponse approve(UUID approvalId, UUID approverId) {
        ApprovalRequestEntity entity = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + approvalId));

        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Approval request is not pending. Current status: " + entity.getStatus());
        }

        entity.setStatus("APPROVED");
        entity.setApprovedBy(approverId);
        entity.setResolvedAt(Instant.now());

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        log.info("Approval approved: id={}, approvedBy={}", approvalId, approverId);
        return toResponse(saved);
    }

    @Transactional
    public ApprovalResponse reject(UUID approvalId, UUID rejecterId, String reason) {
        ApprovalRequestEntity entity = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + approvalId));

        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Approval request is not pending. Current status: " + entity.getStatus());
        }

        entity.setStatus("REJECTED");
        entity.setApprovedBy(rejecterId);
        entity.setRejectedReason(reason);
        entity.setResolvedAt(Instant.now());

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        log.info("Approval rejected: id={}, rejectedBy={}, reason={}", approvalId, rejecterId, reason);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> listPending() {
        return approvalRequestRepository.findByStatus("PENDING").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> listAll() {
        return approvalRequestRepository.findAllByOrderByRequestedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ApprovalResponse toResponse(ApprovalRequestEntity entity) {
        return ApprovalResponse.builder()
                .id(entity.getId())
                .type(entity.getType())
                .resourceId(entity.getResourceId())
                .status(entity.getStatus())
                .requestedBy(entity.getRequestedBy())
                .submittedBy(entity.getSubmittedBy())
                .approvedBy(entity.getApprovedBy())
                .rejectedReason(entity.getRejectedReason())
                .currentLevel(entity.getCurrentLevel())
                .maxLevel(entity.getMaxLevel())
                .cooldownUntil(entity.getCooldownUntil())
                .requestedAt(entity.getRequestedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
