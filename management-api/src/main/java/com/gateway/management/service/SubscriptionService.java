package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.dto.CreateSubscriptionRequest;
import com.gateway.management.dto.SubscriptionResponse;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.PlanEntity;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.PlanRepository;
import com.gateway.management.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ApiRepository apiRepository;
    private final PlanRepository planRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public SubscriptionResponse subscribe(CreateSubscriptionRequest request) {
        UUID applicationId = request.getApplicationId();
        UUID apiId = request.getApiId();
        UUID planId = request.getPlanId();

        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));

        // Check for duplicate subscription (scoped by environment)
        String envSlugCheck = request.getEnvironmentSlug() != null ? request.getEnvironmentSlug() : "dev";
        Optional<SubscriptionEntity> existing =
                subscriptionRepository.findByApplicationIdAndApiId(applicationId, apiId);
        if (existing.isPresent() && envSlugCheck.equals(existing.get().getEnvironmentSlug())) {
            throw new IllegalStateException(
                    "Subscription already exists for application " + applicationId + " and API " + apiId + " in " + envSlugCheck);
        }

        String envSlug = request.getEnvironmentSlug() != null ? request.getEnvironmentSlug() : "dev";
        boolean isProd = "prod".equalsIgnoreCase(envSlug);

        SubscriptionEntity entity = SubscriptionEntity.builder()
                .applicationId(applicationId)
                .api(api)
                .plan(plan)
                .environmentSlug(envSlug)
                .status(isProd ? SubStatus.PENDING : SubStatus.APPROVED)
                .approvedAt(isProd ? null : Instant.now())
                .build();

        SubscriptionEntity saved = subscriptionRepository.save(entity);
        log.info("Subscription created: id={}, appId={}, apiId={}", saved.getId(), applicationId, apiId);

        publishDomainEvent("subscription.created", saved.getId().toString());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(UUID applicationId, UUID apiId, String status) {
        Stream<SubscriptionEntity> stream;

        if (applicationId != null && apiId != null) {
            stream = subscriptionRepository.findByApplicationIdAndApiId(applicationId, apiId)
                    .stream();
        } else if (applicationId != null) {
            stream = subscriptionRepository.findByApplicationId(applicationId).stream();
        } else if (apiId != null) {
            stream = subscriptionRepository.findByApiId(apiId).stream();
        } else {
            stream = subscriptionRepository.findAll().stream();
        }

        if (status != null && !status.isBlank()) {
            SubStatus subStatus = SubStatus.valueOf(status.toUpperCase());
            stream = stream.filter(s -> s.getStatus() == subStatus);
        }

        return stream.map(this::toResponse).toList();
    }

    @Transactional
    public void unsubscribe(UUID id) {
        SubscriptionEntity entity = subscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + id));

        subscriptionRepository.delete(entity);
        log.info("Subscription deleted: id={}", id);

        publishDomainEvent("subscription.deleted", id.toString());
    }

    // ── Admin Subscription Actions ───────────────────────────────────────

    @Transactional
    public SubscriptionResponse approveSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findSubscriptionOrThrow(id);
        validateTransition(entity, SubStatus.APPROVED, SubStatus.PENDING);

        entity.setStatus(SubStatus.APPROVED);
        entity.setApprovedAt(Instant.now());
        entity.setReason(reason);
        setReviewer(entity);

        subscriptionRepository.save(entity);
        log.info("Subscription approved: id={}", id);
        publishDomainEvent("subscription.approved", id.toString());
        return toResponse(entity);
    }

    @Transactional
    public SubscriptionResponse rejectSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findSubscriptionOrThrow(id);
        validateTransition(entity, SubStatus.REJECTED, SubStatus.PENDING);

        entity.setStatus(SubStatus.REJECTED);
        entity.setReason(reason);
        setReviewer(entity);

        subscriptionRepository.save(entity);
        log.info("Subscription rejected: id={}, reason={}", id, reason);
        publishDomainEvent("subscription.rejected", id.toString());
        return toResponse(entity);
    }

    @Transactional
    public SubscriptionResponse suspendSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findSubscriptionOrThrow(id);
        validateTransition(entity, SubStatus.SUSPENDED, SubStatus.APPROVED, SubStatus.ACTIVE);

        entity.setStatus(SubStatus.SUSPENDED);
        entity.setReason(reason);
        setReviewer(entity);

        subscriptionRepository.save(entity);
        log.info("Subscription suspended: id={}, reason={}", id, reason);
        publishDomainEvent("subscription.suspended", id.toString());
        return toResponse(entity);
    }

    @Transactional
    public SubscriptionResponse resumeSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findSubscriptionOrThrow(id);
        validateTransition(entity, SubStatus.APPROVED, SubStatus.SUSPENDED);

        entity.setStatus(SubStatus.APPROVED);
        entity.setReason(reason);
        setReviewer(entity);

        subscriptionRepository.save(entity);
        log.info("Subscription resumed: id={}", id);
        publishDomainEvent("subscription.resumed", id.toString());
        return toResponse(entity);
    }

    @Transactional
    public SubscriptionResponse revokeSubscription(UUID id, String reason) {
        SubscriptionEntity entity = findSubscriptionOrThrow(id);
        validateTransition(entity, SubStatus.CANCELLED, SubStatus.APPROVED, SubStatus.ACTIVE, SubStatus.SUSPENDED, SubStatus.PENDING);

        entity.setStatus(SubStatus.CANCELLED);
        entity.setReason(reason);
        setReviewer(entity);

        subscriptionRepository.save(entity);
        log.info("Subscription revoked: id={}, reason={}", id, reason);
        publishDomainEvent("subscription.revoked", id.toString());
        return toResponse(entity);
    }

    private SubscriptionEntity findSubscriptionOrThrow(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + id));
    }

    private void validateTransition(SubscriptionEntity entity, SubStatus target, SubStatus... allowedFrom) {
        for (SubStatus allowed : allowedFrom) {
            if (entity.getStatus() == allowed) return;
        }
        throw new IllegalStateException(
                "Cannot transition subscription from " + entity.getStatus() + " to " + target);
    }

    private void setReviewer(SubscriptionEntity entity) {
        String actorId = SecurityContextHelper.getCurrentUserId();
        if (actorId != null) {
            try {
                entity.setReviewedBy(UUID.fromString(actorId));
            } catch (IllegalArgumentException ignored) {
                // actor ID might not be a UUID
            }
        }
        entity.setReviewedAt(Instant.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void publishDomainEvent(String eventType, String resourceId) {
        String actorId = SecurityContextHelper.getCurrentUserId();
        eventPublisher.publish(
                RabbitMQExchanges.PLATFORM_EVENTS,
                eventType,
                new SubscriptionDomainEvent(eventType, actorId, resourceId));
    }

    private SubscriptionResponse toResponse(SubscriptionEntity entity) {
        return SubscriptionResponse.builder()
                .id(entity.getId())
                .applicationId(entity.getApplicationId())
                .apiId(entity.getApi().getId())
                .apiName(entity.getApi().getName())
                .planId(entity.getPlan().getId())
                .planName(entity.getPlan().getName())
                .environmentSlug(entity.getEnvironmentSlug())
                .status(entity.getStatus())
                .reason(entity.getReason())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .approvedAt(entity.getApprovedAt())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // ── Inner event class ────────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class SubscriptionDomainEvent extends BaseEvent {
        private String resourceId;

        SubscriptionDomainEvent(String eventType, String actorId, String resourceId) {
            super(eventType, actorId, null);
            this.resourceId = resourceId;
        }
    }
}
