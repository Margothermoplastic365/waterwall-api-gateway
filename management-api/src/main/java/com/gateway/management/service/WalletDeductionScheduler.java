package com.gateway.management.service;

import com.gateway.management.entity.PlanEntity;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.SubscriptionRepository;
import com.gateway.management.repository.WalletRepository;
import jakarta.persistence.EntityManager;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletDeductionScheduler {

    private final PlatformSettingsService platformSettingsService;
    private final SubscriptionRepository subscriptionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final EntityManager entityManager;

    private volatile Instant lastRunTime = null;

    private static final int DEFAULT_INTERVAL_MINUTES = 5;

    /**
     * Runs every minute but only processes if enough time has passed
     * based on the admin-configured interval. This allows dynamic interval
     * changes without restart.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    @Transactional
    public void deductUsage() {
        if (!platformSettingsService.isPayAsYouGoMode()) {
            return;
        }

        int intervalMinutes = platformSettingsService.getWalletDeductionIntervalMinutes();
        if (intervalMinutes <= 0) intervalMinutes = DEFAULT_INTERVAL_MINUTES;

        Instant now = Instant.now();

        // Skip if not enough time has passed since last run
        if (lastRunTime != null) {
            long minutesSinceLastRun = ChronoUnit.MINUTES.between(lastRunTime, now);
            if (minutesSinceLastRun < intervalMinutes) {
                return;
            }
        }

        Instant since = lastRunTime != null ? lastRunTime : now.minus(intervalMinutes, ChronoUnit.MINUTES);
        lastRunTime = now;

        log.debug("Wallet deduction running: window {} to {} (interval={}min)", since, now, intervalMinutes);

        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByStatus(SubStatus.APPROVED);

        // Collect unique app IDs and build owner mapping
        Set<UUID> appIds = new HashSet<>();
        for (SubscriptionEntity sub : subscriptions) {
            appIds.add(sub.getApplicationId());
        }
        Map<UUID, UUID> appToUser = resolveAppOwners(appIds);

        // Process each subscription individually (per app + per API)
        int deducted = 0;
        // Track which wallet owners we've already charged per this cycle to aggregate totals
        Map<UUID, BigDecimal> totalCostPerOwner = new HashMap<>();
        Map<UUID, StringBuilder> descPerOwner = new HashMap<>();

        for (SubscriptionEntity sub : subscriptions) {
            UUID appId = sub.getApplicationId();
            UUID apiId = sub.getApi().getId();
            PlanEntity plan = sub.getPlan();
            UUID walletOwnerId = appToUser.getOrDefault(appId, appId);

            if (plan == null) continue;

            String model = plan.getPricingModel() != null ? plan.getPricingModel().toUpperCase() : "FREE";
            if ("FREE".equals(model) || "FLAT_RATE".equals(model)) continue;

            try {
                // Count requests for this specific app + API in this window
                long requestCount = countRequestsForApi(appId, apiId, since, now);
                if (requestCount == 0) continue;

                // Count monthly total for this app + API (for free tier calculation)
                long monthlyUsage = countMonthlyRequestsForApi(appId, apiId);
                long usageBefore = monthlyUsage - requestCount;

                // Calculate incremental cost using PricingCalculator
                BigDecimal costTotal = PricingCalculator.calculateCost(plan, monthlyUsage);
                BigDecimal costBefore = PricingCalculator.calculateCost(plan, usageBefore);

                // For TIERED, subtract base fee (subscription concept)
                if ("TIERED".equals(model)) {
                    BigDecimal baseFee = plan.getPriceAmount() != null ? plan.getPriceAmount() : BigDecimal.ZERO;
                    costTotal = costTotal.subtract(baseFee);
                    costBefore = costBefore.subtract(baseFee);
                }

                BigDecimal cost = costTotal.subtract(costBefore).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

                if (cost.signum() <= 0) continue;

                BigDecimal rate = plan.getOverageRate() != null ? plan.getOverageRate() : BigDecimal.ZERO;
                String apiName = sub.getApi().getName() != null ? sub.getApi().getName() : apiId.toString().substring(0, 8);

                totalCostPerOwner.merge(walletOwnerId, cost, BigDecimal::add);
                descPerOwner.computeIfAbsent(walletOwnerId, k -> new StringBuilder())
                        .append(requestCount).append(" calls to ").append(apiName)
                        .append(" @ ").append(rate).append("/req (").append(model).append("); ");

            } catch (Exception e) {
                log.error("Wallet deduction calc error for app={} api={}: {}", appId, apiId, e.getMessage());
            }
        }

        // Now deduct aggregated costs per wallet owner
        for (Map.Entry<UUID, BigDecimal> entry : totalCostPerOwner.entrySet()) {
            UUID ownerId = entry.getKey();
            BigDecimal totalCost = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            String desc = descPerOwner.get(ownerId).toString();

            try {
                walletService.deduct(ownerId, totalCost,
                        "USAGE-" + now.toEpochMilli(), desc.trim(), null);
                deducted++;
            } catch (IllegalStateException e) {
                log.warn("Wallet deduction failed for owner={}: {}", ownerId, e.getMessage());
            } catch (Exception e) {
                log.error("Wallet deduction error for owner={}: {}", ownerId, e.getMessage());
            }
        }

        if (deducted > 0) {
            log.info("Wallet deduction complete: {} consumers charged", deducted);
        }
    }

    private long countRequestsForApi(UUID appId, UUID apiId, Instant since, Instant until) {
        try {
            return ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM analytics.request_logs " +
                    "WHERE consumer_id = :appId AND api_id = :apiId " +
                    "AND (mock_mode IS NULL OR mock_mode = false) " +
                    "AND created_at >= :since AND created_at < :until"
            ).setParameter("appId", appId)
             .setParameter("apiId", apiId)
             .setParameter("since", java.sql.Timestamp.from(since))
             .setParameter("until", java.sql.Timestamp.from(until))
             .getSingleResult()).longValue();
        } catch (Exception e) {
            log.warn("Failed to count requests for app={} api={}: {}", appId, apiId, e.getMessage());
            return 0;
        }
    }

    private long countMonthlyRequestsForApi(UUID appId, UUID apiId) {
        try {
            java.time.LocalDate monthStart = java.time.LocalDate.now().withDayOfMonth(1);
            return ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM analytics.request_logs " +
                    "WHERE consumer_id = :appId AND api_id = :apiId " +
                    "AND (mock_mode IS NULL OR mock_mode = false) " +
                    "AND created_at >= CAST(:monthStart AS timestamp)"
            ).setParameter("appId", appId)
             .setParameter("apiId", apiId)
             .setParameter("monthStart", monthStart.toString())
             .getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }


    /**
     * Maps application IDs to the user IDs that own them.
     * Wallets are keyed by user ID, but request logs use app ID as consumer_id.
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, UUID> resolveAppOwners(java.util.Set<UUID> appIds) {
        Map<UUID, UUID> mapping = new HashMap<>();
        if (appIds.isEmpty()) return mapping;

        try {
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT id, user_id FROM identity.applications WHERE id IN (:appIds)"
            ).setParameter("appIds", new ArrayList<>(appIds)).getResultList();

            for (Object[] row : rows) {
                UUID appId = (UUID) row[0];
                UUID userId = (UUID) row[1];
                if (userId != null) {
                    mapping.put(appId, userId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve app owners: {}", e.getMessage());
        }
        return mapping;
    }
}
