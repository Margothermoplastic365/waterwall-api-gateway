package com.gateway.management.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.*;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApprovalChainEntity;
import com.gateway.management.entity.ApprovalRequestEntity;
import com.gateway.management.repository.ApprovalChainRepository;
import com.gateway.management.service.ApiService;
import com.gateway.management.service.VersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;
    private final ApiService apiService;
    private final ApprovalChainRepository approvalChainRepository;

    /**
     * Create a new version by cloning an existing published API.
     */
    @PostMapping
    @RequiresPermission("version:create")
    public ResponseEntity<ApiResponse> createVersion(
            @Valid @RequestBody CreateVersionRequest request,
            GatewayAuthentication auth) {
        UUID userId = UUID.fromString(auth.getUserId());
        ApiEntity created = versionService.createVersion(request.getSourceVersionId(), request.getNewVersion(), userId);
        ApiResponse response = apiService.getApi(created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all versions for an API group.
     */
    @GetMapping
    public ResponseEntity<List<ApiResponse>> listVersions(@RequestParam UUID apiGroupId) {
        List<ApiEntity> versions = versionService.listVersions(apiGroupId);
        List<ApiResponse> responses = versions.stream()
                .map(v -> apiService.getApi(v.getId()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Submit a DRAFT version for review.
     */
    @PostMapping("/{versionId}/submit")
    @RequiresPermission("version:submit")
    public ResponseEntity<ApprovalRequestEntity> submitForReview(
            @PathVariable UUID versionId,
            GatewayAuthentication auth) {
        UUID userId = UUID.fromString(auth.getUserId());
        ApprovalRequestEntity approval = versionService.submitForReview(versionId, userId);
        return ResponseEntity.ok(approval);
    }

    /**
     * Review (approve/reject) a version at the current approval level.
     */
    @PostMapping("/review/{approvalRequestId}")
    @RequiresPermission("version:review_l1")
    public ResponseEntity<ApprovalRequestEntity> reviewVersion(
            @PathVariable UUID approvalRequestId,
            @RequestBody ReviewActionRequest request,
            GatewayAuthentication auth) {
        UUID reviewerId = UUID.fromString(auth.getUserId());
        ApprovalRequestEntity result = versionService.reviewVersion(
                approvalRequestId, reviewerId, request.isApproved(), request.getRejectionReason());
        return ResponseEntity.ok(result);
    }

    /**
     * Deprecate an ACTIVE version.
     */
    @PostMapping("/{versionId}/deprecate")
    @RequiresPermission("version:deprecate")
    public ResponseEntity<ApiResponse> deprecateVersion(
            @PathVariable UUID versionId,
            @RequestBody DeprecateVersionRequest request) {
        versionService.deprecateVersion(versionId, request.getMessage(), request.getSuccessorVersionId());
        return ResponseEntity.ok(apiService.getApi(versionId));
    }

    /**
     * Retire a DEPRECATED or ACTIVE version.
     */
    @PostMapping("/{versionId}/retire")
    @RequiresPermission("version:retire")
    public ResponseEntity<ApiResponse> retireVersion(@PathVariable UUID versionId) {
        versionService.retireVersion(versionId);
        return ResponseEntity.ok(apiService.getApi(versionId));
    }

    /**
     * Get approval chain for an API.
     */
    @GetMapping("/{apiId}/approval-chain")
    public ResponseEntity<List<ApprovalChainEntity>> getApprovalChain(@PathVariable UUID apiId) {
        return ResponseEntity.ok(approvalChainRepository.findByApiIdOrderByLevelAsc(apiId));
    }
}
