package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.*;
import com.gateway.management.entity.*;
import com.gateway.management.entity.enums.*;
import com.gateway.management.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionService {

    private static final int MAX_ACTIVE_VERSIONS = 5;

    private final ApiRepository apiRepository;
    private final RouteRepository routeRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalChainRepository approvalChainRepository;

    // ── Create Version ──────────────────────────────────────────

    @Transactional
    public ApiEntity createVersion(UUID sourceVersionId, String newVersion, UUID createdBy) {
        ApiEntity source = apiRepository.findById(sourceVersionId)
                .orElseThrow(() -> new EntityNotFoundException("Source API not found: " + sourceVersionId));

        if (source.getStatus() != ApiStatus.PUBLISHED) {
            throw new IllegalStateException("Can only create versions from PUBLISHED APIs");
        }

        UUID groupId = source.getApiGroupId() != null ? source.getApiGroupId() : source.getId();

        // Count non-RETIRED versions in this group
        long activeCount = apiRepository.findAll().stream()
                .filter(a -> groupId.equals(a.getApiGroupId()))
                .filter(a -> a.getVersionStatus() != VersionStatus.RETIRED)
                .count();

        if (activeCount >= MAX_ACTIVE_VERSIONS) {
            throw new IllegalStateException("Maximum " + MAX_ACTIVE_VERSIONS + " active versions allowed. Retire an older version first.");
        }

        // Clone the API entity
        ApiEntity newApi = ApiEntity.builder()
                .name(source.getName())
                .version(newVersion)
                .description(source.getDescription())
                .status(ApiStatus.PUBLISHED)
                .visibility(source.getVisibility())
                .protocolType(source.getProtocolType())
                .tags(source.getTags() != null ? new ArrayList<>(source.getTags()) : null)
                .category(source.getCategory())
                .orgId(source.getOrgId())
                .authMode(source.getAuthMode())
                .allowAnonymous(source.getAllowAnonymous())
                .gatewayConfig(source.getGatewayConfig())
                .apiGroupId(groupId)
                .apiGroupName(source.getApiGroupName() != null ? source.getApiGroupName() : source.getName())
                .sensitivity(source.getSensitivity())
                .versionStatus(VersionStatus.DRAFT)
                .createdBy(createdBy)
                .build();

        ApiEntity saved = apiRepository.save(newApi);

        // Clone routes
        List<RouteEntity> sourceRoutes = routeRepository.findByApiId(source.getId());
        for (RouteEntity route : sourceRoutes) {
            RouteEntity cloned = RouteEntity.builder()
                    .api(saved)
                    .path(route.getPath())
                    .method(route.getMethod())
                    .upstreamUrl(route.getUpstreamUrl())
                    .authTypes(route.getAuthTypes())
                    .priority(route.getPriority())
                    .stripPrefix(route.getStripPrefix())
                    .enabled(route.getEnabled())
                    .build();
            routeRepository.save(cloned);
        }

        // Create approval chain based on sensitivity
        ensureApprovalChain(saved.getId(), saved.getSensitivity());

        log.info("Version created: id={}, version={}, group={}", saved.getId(), newVersion, groupId);
        return saved;
    }

    // ── List Versions ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApiEntity> listVersions(UUID apiGroupId) {
        return apiRepository.findAll().stream()
                .filter(a -> apiGroupId.equals(a.getApiGroupId()))
                .sorted(Comparator.comparing(ApiEntity::getCreatedAt).reversed())
                .toList();
    }

    // ── Submit for Review ───────────────────────────────────────

    @Transactional
    public ApprovalRequestEntity submitForReview(UUID apiId, UUID submittedBy) {
        ApiEntity api = findApiOrThrow(apiId);

        // Allow submit if either versionStatus is DRAFT or api status is DRAFT/CREATED
        boolean isDraft = api.getVersionStatus() == VersionStatus.DRAFT
                || api.getStatus() == ApiStatus.DRAFT
                || api.getStatus() == ApiStatus.CREATED;

        if (!isDraft) {
            throw new IllegalStateException("Only DRAFT APIs/versions can be submitted for review. Current status: "
                    + api.getStatus() + ", versionStatus: " + api.getVersionStatus());
        }

        api.setVersionStatus(VersionStatus.IN_REVIEW);
        api.setStatus(ApiStatus.IN_REVIEW);
        apiRepository.save(api);

        List<ApprovalChainEntity> chain = approvalChainRepository.findByApiIdOrderByLevelAsc(apiId);
        int maxLevel = chain.isEmpty() ? 1 : chain.size();

        ApprovalRequestEntity approval = ApprovalRequestEntity.builder()
                .type("VERSION_PUBLISH")
                .resourceId(apiId)
                .status("PENDING")
                .submittedBy(submittedBy)
                .requestedBy(submittedBy)
                .currentLevel(1)
                .maxLevel(maxLevel)
                .requestedAt(Instant.now())
                .build();

        ApprovalRequestEntity saved = approvalRequestRepository.save(approval);
        log.info("Version submitted for review: apiId={}, approvalId={}, levels={}", apiId, saved.getId(), maxLevel);
        return saved;
    }

    // ── Review Version ──────────────────────────────────────────

    @Transactional
    public ApprovalRequestEntity reviewVersion(UUID approvalRequestId, UUID reviewerId, boolean approved, String reason) {
        ApprovalRequestEntity request = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Approval request is not pending");
        }

        // Maker-checker: submitter cannot approve
        if (request.getSubmittedBy() != null && request.getSubmittedBy().equals(reviewerId)) {
            throw new IllegalStateException("Submitter cannot approve their own submission (maker-checker violation)");
        }

        // Validate reviewer has the correct permission for the current approval level
        int currentLevel = request.getCurrentLevel();
        approvalChainRepository.findByApiIdAndLevel(request.getResourceId(), currentLevel)
                .ifPresent(chain -> {
                    String required = chain.getRequiredPermission();
                    if (required != null && !SecurityContextHelper.hasPermission(required)) {
                        throw new IllegalStateException(
                                "Level " + currentLevel + " review requires permission: " + required
                                        + " (" + chain.getDescription() + ")");
                    }
                });

        ApiEntity api = findApiOrThrow(request.getResourceId());

        if (!approved) {
            // Rejected — back to DRAFT
            request.setStatus("REJECTED");
            request.setApprovedBy(reviewerId);
            request.setRejectedReason(reason);
            request.setResolvedAt(Instant.now());
            approvalRequestRepository.save(request);

            api.setVersionStatus(VersionStatus.DRAFT);
            apiRepository.save(api);

            log.info("Version rejected: apiId={}, reviewer={}, reason={}", api.getId(), reviewerId, reason);
            return request;
        }

        // Approved at current level
        int maxLevel = request.getMaxLevel();

        if (currentLevel < maxLevel) {
            // More levels to go
            request.setCurrentLevel(currentLevel + 1);
            approvalRequestRepository.save(request);
            log.info("Version approved at level {}/{}: apiId={}", currentLevel, maxLevel, api.getId());
            return request;
        }

        // Final level approved
        request.setStatus("APPROVED");
        request.setApprovedBy(reviewerId);
        request.setResolvedAt(Instant.now());

        // Check for CRITICAL cooldown
        if (api.getSensitivity() == Sensitivity.CRITICAL) {
            request.setCooldownUntil(Instant.now().plus(24, ChronoUnit.HOURS));
            approvalRequestRepository.save(request);
            log.info("Version approved with 24h cooldown: apiId={}", api.getId());
            return request;
        }

        approvalRequestRepository.save(request);

        // Activate version and publish API
        api.setVersionStatus(VersionStatus.ACTIVE);
        api.setStatus(ApiStatus.PUBLISHED);
        apiRepository.save(api);

        log.info("Version activated: apiId={}, version={}", api.getId(), api.getVersion());
        return request;
    }

    // ── Deprecate Version ───────────────────────────────────────

    @Transactional
    public ApiEntity deprecateVersion(UUID apiId, String message, UUID successorVersionId) {
        ApiEntity api = findApiOrThrow(apiId);

        if (api.getVersionStatus() != VersionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE versions can be deprecated");
        }

        api.setVersionStatus(VersionStatus.DEPRECATED);
        api.setDeprecatedMessage(message);
        api.setSuccessorVersionId(successorVersionId);
        apiRepository.save(api);

        log.info("Version deprecated: apiId={}, successor={}", apiId, successorVersionId);
        return api;
    }

    // ── Retire Version ──────────────────────────────────────────

    @Transactional
    public ApiEntity retireVersion(UUID apiId) {
        ApiEntity api = findApiOrThrow(apiId);

        if (api.getVersionStatus() != VersionStatus.DEPRECATED && api.getVersionStatus() != VersionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE or DEPRECATED versions can be retired");
        }

        api.setVersionStatus(VersionStatus.RETIRED);
        apiRepository.save(api);

        // Disable all routes
        List<RouteEntity> routes = routeRepository.findByApiId(apiId);
        for (RouteEntity route : routes) {
            route.setEnabled(false);
            routeRepository.save(route);
        }

        log.info("Version retired: apiId={}", apiId);
        return api;
    }

    // ── Helpers ─────────────────────────────────────────────────

    private ApiEntity findApiOrThrow(UUID id) {
        return apiRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + id));
    }

    private void ensureApprovalChain(UUID apiId, Sensitivity sensitivity) {
        List<ApprovalChainEntity> existing = approvalChainRepository.findByApiIdOrderByLevelAsc(apiId);
        if (!existing.isEmpty()) return;

        List<ApprovalChainEntity> chain = new ArrayList<>();

        chain.add(ApprovalChainEntity.builder()
                .apiId(apiId).level(1)
                .requiredPermission("version:review_l1")
                .description("Technical Review")
                .build());

        if (sensitivity == Sensitivity.MEDIUM || sensitivity == Sensitivity.HIGH || sensitivity == Sensitivity.CRITICAL) {
            chain.add(ApprovalChainEntity.builder()
                    .apiId(apiId).level(2)
                    .requiredPermission("version:review_l2")
                    .description("Compliance Review")
                    .build());
        }

        if (sensitivity == Sensitivity.HIGH || sensitivity == Sensitivity.CRITICAL) {
            chain.add(ApprovalChainEntity.builder()
                    .apiId(apiId).level(3)
                    .requiredPermission("version:review_l3")
                    .description("Platform Sign-off")
                    .build());
        }

        approvalChainRepository.saveAll(chain);
    }
}
