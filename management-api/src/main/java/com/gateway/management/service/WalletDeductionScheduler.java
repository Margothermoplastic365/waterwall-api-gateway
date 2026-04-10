package com.gateway.management.service;

import com.gateway.management.entity.PlanEntity;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.WalletEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.SubscriptionRepository;
import com.gateway.management.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Runs at a configurable interval (default every 5 minutes).
 * In PAY_AS_YOU_GO mode, counts each consumer's API requests since the last deduction
 * and deducts the cost from their wallet based on the plan's per-request rate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletDeductionScheduler {

    private final PlatformSettingsService platformSettingsService;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final EntityManager entityManager;

    /**
     * Track the last deduction time per consumer to know the window for counting requests.
     * In production this should be persisted, but for now in-memory with DB fallback.
     */
    private volatile Instant lastRunTime = null;

    @Scheduled(fixedDelayString = "#{@walletDeductionDelay}")
    @Transactional
    public void deductUsage() {
        if (!platformSettingsService.isPayAsYouGoMode()) {
            return;
        }

        Instant now = Instant.now();
        Instant since = lastRunTime != null ? lastRunTime : now.minus(
                platformSettingsService.getWalletDeductionIntervalMinutes(), ChronoUnit.MINUTES);
        lastRunTime = now;

        log.debug("Wallet deduction running: window {} to {}", since, now);

        // Get all active subscriptions
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByStatus(SubStatus.APPROVED);

        // Group by consumer (application_id) to aggregate across APIs
        Map<UUID, List<SubscriptionEntity>> byConsumer = new HashMap<>();
        for (SubscriptionEntity sub : subscriptions) {
            byConsumer.computeIfAbsent(sub.getApplicationId(), k -> new ArrayList<>()).add(sub);
        }

        int deducted = 0;
        for (Map.Entry<UUID, List<SubscriptionEntity>> entry : byConsumer.entrySet()) {
            UUID consumerId = entry.getKey();
            List<SubscriptionEntity> consumerSubs = entry.getValue();

            try {
                // Count requests in the time window for this consumer
                long requestCount = countRequests(consumerId, since, now);
                if (requestCount == 0) continue;

                // Resolve the per-request rate from the plan
                BigDecimal perRequestRate = resolvePerRequestRate(consumerSubs);
                if (perRequestRate == null || perRequestRate.signum() <= 0) continue;

                // Calculate free tier remaining (monthly)
                long freeRequests = resolveFreeRequests(consumerSubs);
                long monthlyUsage = countMonthlyRequests(consumerId);
                long billableRequests = Math.max(0, requestCount - Math.max(0, freeRequests - (monthlyUsage - requestCount)));

                if (billableRequests <= 0) continue;

                BigDecimal cost = perRequestRate.multiply(BigDecimal.valueOf(billableRequests))
                        .setScale(2, RoundingMode.HALF_UP);

                if (cost.signum() <= 0) continue;

                // Deduct from wallet
                walletService.deduct(consumerId, cost,
                        "USAGE-" + now.toEpochMilli(),
                        billableRequests + " API requests @ " + perRequestRate + "/req",
                        null);
                deducted++;

            } catch (IllegalStateException e) {
                // Insufficient balance — logged by WalletService, low balance alert already sent
                log.warn("Wallet deduction failed for consumer={}: {}", consumerId, e.getMessage());
            } catch (Exception e) {
                log.error("Wallet deduction error for consumer={}: {}", consumerId, e.getMessage());
            }
        }

        if (deducted > 0) {
            log.info("Wallet deduction complete: {} consumers charged", deducted);
        }
    }

    private long countRequests(UUID consumerId, Instant since, Instant until) {
        try {
            Query query = entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM analytics.request_logs " +
                    "WHERE consumer_id = :consumerId " +
                    "AND (mock_mode IS NULL OR mock_mode = false) " +
                    "AND created_at >= :since AND created_at < :until"
            );
            query.setParameter("consumerId", consumerId);
            query.setParameter("since", java.sql.Timestamp.from(since));
            query.setParameter("until", java.sql.Timestamp.from(until));
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            log.warn("Failed to count requests for consumer={}: {}", consumerId, e.getMessage());
            return 0;
        }
    }

    private long countMonthlyRequests(UUID consumerId) {
        try {
            java.time.LocalDate monthStart = java.time.LocalDate.now().withDayOfMonth(1);
            Query query = entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM analytics.request_logs " +
                    "WHERE consumer_id = :consumerId " +
                    "AND (mock_mode IS NULL OR mock_mode = false) " +
                    "AND created_at >= CAST(:monthStart AS timestamp)"
            );
            query.setParameter("consumerId", consumerId);
            query.setParameter("monthStart", monthStart.toString());
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal resolvePerRequestRate(List<SubscriptionEntity> subs) {
        for (SubscriptionEntity sub : subs) {
            PlanEntity plan = sub.getPlan();
            if (plan != null && plan.getOverageRate() != null && plan.getOverageRate().signum() > 0) {
                return plan.getOverageRate();
            }
        }
        return null;
    }

    private long resolveFreeRequests(List<SubscriptionEntity> subs) {
        for (SubscriptionEntity sub : subs) {
            PlanEntity plan = sub.getPlan();
            if (plan != null && plan.getIncludedRequests() != null) {
                return plan.getIncludedRequests();
            }
        }
        return 0;
    }
}
