package com.gateway.management.service;

import com.gateway.management.entity.PlanEntity;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Static utility class for calculating API usage costs based on a plan's pricing model.
 *
 * Supported models:
 *   FREE       - always 0
 *   FLAT_RATE  - fixed priceAmount regardless of usage
 *   PAY_PER_USE - requestCount * overageRate
 *   TIERED     - includedRequests at priceAmount, overage at overageRate
 *   FREEMIUM   - includedRequests free, overage at overageRate
 */
@Slf4j
public final class PricingCalculator {

    private PricingCalculator() {
        // utility class
    }

    public static BigDecimal calculateCost(PlanEntity plan, long requestCount) {
        if (plan == null) {
            log.warn("No plan available; defaulting cost to zero");
            return BigDecimal.ZERO;
        }

        String model = plan.getPricingModel() != null ? plan.getPricingModel().toUpperCase() : "FREE";
        BigDecimal priceAmount = plan.getPriceAmount() != null ? plan.getPriceAmount() : BigDecimal.ZERO;
        BigDecimal overageRate = plan.getOverageRate() != null ? plan.getOverageRate() : BigDecimal.ZERO;
        long includedRequests = plan.getIncludedRequests() != null ? plan.getIncludedRequests() : 0L;

        switch (model) {
            case "FREE":
                return BigDecimal.ZERO;

            case "FLAT_RATE":
                return priceAmount;

            case "PAY_PER_USE":
                return overageRate.multiply(BigDecimal.valueOf(requestCount))
                        .setScale(2, RoundingMode.HALF_UP);

            case "TIERED":
                BigDecimal tieredTotal = priceAmount;
                if (requestCount > includedRequests) {
                    long overage = requestCount - includedRequests;
                    tieredTotal = tieredTotal.add(
                            overageRate.multiply(BigDecimal.valueOf(overage))
                    );
                }
                return tieredTotal.setScale(2, RoundingMode.HALF_UP);

            case "FREEMIUM":
                if (requestCount <= includedRequests) {
                    return BigDecimal.ZERO;
                }
                long overageCount = requestCount - includedRequests;
                return overageRate.multiply(BigDecimal.valueOf(overageCount))
                        .setScale(2, RoundingMode.HALF_UP);

            default:
                log.warn("Unknown pricing model '{}'; defaulting cost to zero", model);
                return BigDecimal.ZERO;
        }
    }
}
