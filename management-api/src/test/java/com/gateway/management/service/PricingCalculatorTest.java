package com.gateway.management.service;

import com.gateway.management.entity.PlanEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorTest {

    private PlanEntity planWith(String model, BigDecimal price, BigDecimal overage, Long included) {
        return PlanEntity.builder()
                .pricingModel(model)
                .priceAmount(price)
                .overageRate(overage)
                .includedRequests(included)
                .build();
    }

    @Test
    void freePlan_alwaysReturnsZero() {
        PlanEntity plan = planWith("FREE", new BigDecimal("9.99"), new BigDecimal("0.01"), 1000L);
        assertThat(PricingCalculator.calculateCost(plan, 5000)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void flatRate_returnsFixedPrice() {
        PlanEntity plan = planWith("FLAT_RATE", new BigDecimal("29.99"), BigDecimal.ZERO, null);
        assertThat(PricingCalculator.calculateCost(plan, 0)).isEqualByComparingTo("29.99");
        assertThat(PricingCalculator.calculateCost(plan, 10000)).isEqualByComparingTo("29.99");
    }

    @Test
    void payPerUse_chargesPerRequest() {
        PlanEntity plan = planWith("PAY_PER_USE", BigDecimal.ZERO, new BigDecimal("0.005"), null);
        // 200 * 0.005 = 1.00
        assertThat(PricingCalculator.calculateCost(plan, 200)).isEqualByComparingTo("1.00");
    }

    @Test
    void tiered_belowIncluded_returnsBaseFeeOnly() {
        PlanEntity plan = planWith("TIERED", new BigDecimal("10.00"), new BigDecimal("0.01"), 1000L);
        assertThat(PricingCalculator.calculateCost(plan, 500)).isEqualByComparingTo("10.00");
    }

    @Test
    void tiered_aboveIncluded_addsOverageCharge() {
        PlanEntity plan = planWith("TIERED", new BigDecimal("10.00"), new BigDecimal("0.01"), 1000L);
        // 10.00 + (500 * 0.01) = 10.00 + 5.00 = 15.00
        assertThat(PricingCalculator.calculateCost(plan, 1500)).isEqualByComparingTo("15.00");
    }

    @Test
    void freemium_withinIncluded_returnsZero() {
        PlanEntity plan = planWith("FREEMIUM", BigDecimal.ZERO, new BigDecimal("0.02"), 500L);
        assertThat(PricingCalculator.calculateCost(plan, 500)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void freemium_aboveIncluded_chargesOverage() {
        PlanEntity plan = planWith("FREEMIUM", BigDecimal.ZERO, new BigDecimal("0.02"), 500L);
        // (1000 - 500) * 0.02 = 10.00
        assertThat(PricingCalculator.calculateCost(plan, 1000)).isEqualByComparingTo("10.00");
    }

    @Test
    void nullPlan_returnsZero() {
        assertThat(PricingCalculator.calculateCost(null, 1000)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unknownModel_returnsZero() {
        PlanEntity plan = planWith("ENTERPRISE_CUSTOM", new BigDecimal("999.00"), BigDecimal.ZERO, null);
        assertThat(PricingCalculator.calculateCost(plan, 5000)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
