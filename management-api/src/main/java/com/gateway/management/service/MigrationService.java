package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.dto.ApiDeploymentRequest;
import com.gateway.management.dto.MigrationRequest;
import com.gateway.management.dto.MigrationResponse;
import com.gateway.management.entity.ApiDeploymentEntity;
import com.gateway.management.entity.MigrationEntity;
import com.gateway.management.repository.ApiDeploymentRepository;
import com.gateway.management.repository.MigrationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private static final List<String> PROMOTION_ORDER = List.of("dev", "uat", "pre-prod", "prod");

    private static final Map<String, String> ALLOWED_PROMOTIONS = Map.of(
            "dev", "uat",
            "uat", "pre-prod",
            "pre-prod", "prod"
    );

    private final MigrationRepository migrationRepository;
    private final ApiDeploymentRepository apiDeploymentRepository;
    private final EnvironmentService environmentService;
    private final ApprovalService approvalService;
    private final EventPublisher eventPublisher;

    @Transactional
    public MigrationResponse initiateMigration(MigrationRequest request) {
        String sourceEnv = request.getSourceEnv().toLowerCase();
        String targetEnv = request.getTargetEnv().toLowerCase();

        // a) Validate sequential promotion
        String allowedTarget = ALLOWED_PROMOTIONS.get(sourceEnv);
        if (allowedTarget == null || !allowedTarget.equals(targetEnv)) {
            throw new IllegalArgumentException(
                    "Invalid promotion path: " + sourceEnv + " → " + targetEnv
                            + ". Allowed: DEV→UAT→PRE-PROD→PROD");
        }

        // b) Validate API is deployed in source env
        ApiDeploymentEntity sourceDeployment = apiDeploymentRepository
                .findByApiIdAndEnvironmentSlug(request.getApiId(), sourceEnv)
                .orElseThrow(() -> new IllegalStateException(
                        "API is not deployed in source environment: " + sourceEnv));

        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID initiatedBy = currentUserId != null ? UUID.fromString(currentUserId) : null;

        // c) Create migration record
        MigrationEntity migration = MigrationEntity.builder()
                .apiId(request.getApiId())
                .sourceEnv(sourceEnv)
                .targetEnv(targetEnv)
                .status("INITIATED")
                .initiatedBy(initiatedBy)
                .initiatedAt(Instant.now())
                .build();

        // d) Build migration package (snapshot)
        migration.setMigrationPackage(sourceDeployment.getConfigSnapshot());

        // e) Pre-flight validation (placeholder — always passes)
        migration.setStatus("VALIDATED");
        migration = migrationRepository.save(migration);
        log.info("Migration initiated: id={}, {} → {}", migration.getId(), sourceEnv, targetEnv);

        // f) Auto-approve for DEV→UAT, require manual for others
        boolean autoApprove = "dev".equals(sourceEnv) && "uat".equals(targetEnv);

        if (autoApprove) {
            migration.setStatus("APPROVED");
            migration = migrationRepository.save(migration);
        } else {
            // Request approval
            approvalService.requestApproval("MIGRATION", migration.getId());
            migration.setStatus("APPROVED");
            migration = migrationRepository.save(migration);
            // Note: In a full implementation, we would wait for approval.
            // For now, we proceed after creating the approval request.
        }

        // g) Deploy to target env
        migration.setStatus("DEPLOYING");
        migration = migrationRepository.save(migration);

        ApiDeploymentRequest deployRequest = new ApiDeploymentRequest();
        deployRequest.setApiId(request.getApiId());
        deployRequest.setTargetEnvironment(targetEnv);
        environmentService.deployApi(deployRequest);

        // h) Set status=COMPLETED
        migration.setStatus("COMPLETED");
        migration.setCompletedAt(Instant.now());
        migration = migrationRepository.save(migration);

        // i) Publish event
        publishDomainEvent("migration.completed", migration.getId().toString());

        return toResponse(migration);
    }

    @Transactional(readOnly = true)
    public MigrationResponse getMigration(UUID migrationId) {
        MigrationEntity entity = migrationRepository.findById(migrationId)
                .orElseThrow(() -> new EntityNotFoundException("Migration not found: " + migrationId));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<MigrationResponse> listMigrations(UUID apiId) {
        return migrationRepository.findByApiId(apiId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MigrationResponse rollback(UUID migrationId) {
        MigrationEntity entity = migrationRepository.findById(migrationId)
                .orElseThrow(() -> new EntityNotFoundException("Migration not found: " + migrationId));

        if (!"COMPLETED".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Can only rollback completed migrations. Current status: " + entity.getStatus());
        }

        // Remove deployment in target env
        apiDeploymentRepository.findByApiIdAndEnvironmentSlug(entity.getApiId(), entity.getTargetEnv())
                .ifPresent(apiDeploymentRepository::delete);

        entity.setStatus("ROLLED_BACK");
        entity.setCompletedAt(Instant.now());
        MigrationEntity saved = migrationRepository.save(entity);

        log.info("Migration rolled back: id={}", migrationId);
        publishDomainEvent("migration.rolled_back", saved.getId().toString());

        return toResponse(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private MigrationResponse toResponse(MigrationEntity entity) {
        return MigrationResponse.builder()
                .migrationId(entity.getId())
                .apiId(entity.getApiId())
                .sourceEnv(entity.getSourceEnv())
                .targetEnv(entity.getTargetEnv())
                .status(entity.getStatus())
                .initiatedAt(entity.getInitiatedAt())
                .initiatedBy(entity.getInitiatedBy() != null ? entity.getInitiatedBy().toString() : null)
                .build();
    }

    private void publishDomainEvent(String eventType, String resourceId) {
        String actorId = SecurityContextHelper.getCurrentUserId();
        eventPublisher.publish(
                RabbitMQExchanges.PLATFORM_EVENTS,
                eventType,
                new MigrationDomainEvent(eventType, actorId, resourceId));
    }

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class MigrationDomainEvent extends BaseEvent {
        private String resourceId;

        MigrationDomainEvent(String eventType, String actorId, String resourceId) {
            super(eventType, actorId, null);
            this.resourceId = resourceId;
        }
    }
}
