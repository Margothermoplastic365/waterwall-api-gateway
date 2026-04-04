package com.gateway.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.CreatePlanRequest;
import com.gateway.management.dto.PlanResponse;
import com.gateway.management.dto.QuotaConfig;
import com.gateway.management.dto.RateLimitsConfig;
import com.gateway.management.entity.PlanEntity;
import com.gateway.management.entity.enums.Enforcement;
import com.gateway.management.repository.PlanRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PlanService planService;

    @Test
    void shouldCreatePlan() throws Exception {
        RateLimitsConfig rateLimits = new RateLimitsConfig(100, 6000, 500000, 20);
        QuotaConfig quota = new QuotaConfig(1000000L);

        CreatePlanRequest request = CreatePlanRequest.builder()
                .name("Gold Plan")
                .description("Premium plan")
                .rateLimits(rateLimits)
                .quota(quota)
                .enforcement(Enforcement.STRICT)
                .pricingModel("FLAT_RATE")
                .priceAmount(new BigDecimal("49.99"))
                .currency("USD")
                .billingPeriod("MONTHLY")
                .includedRequests(100000L)
                .overageRate(new BigDecimal("0.001"))
                .build();

        when(objectMapper.writeValueAsString(rateLimits)).thenReturn("{\"requestsPerSecond\":100}");
        when(objectMapper.writeValueAsString(quota)).thenReturn("{\"maxRequestsPerMonth\":1000000}");

        UUID planId = UUID.randomUUID();
        PlanEntity savedEntity = PlanEntity.builder()
                .id(planId)
                .name("Gold Plan")
                .description("Premium plan")
                .rateLimits("{\"requestsPerSecond\":100}")
                .quota("{\"maxRequestsPerMonth\":1000000}")
                .enforcement(Enforcement.STRICT)
                .status("ACTIVE")
                .pricingModel("FLAT_RATE")
                .priceAmount(new BigDecimal("49.99"))
                .currency("USD")
                .billingPeriod("MONTHLY")
                .includedRequests(100000L)
                .overageRate(new BigDecimal("0.001"))
                .build();

        when(planRepository.save(any(PlanEntity.class))).thenReturn(savedEntity);
        when(objectMapper.readValue(eq("{\"requestsPerSecond\":100}"), eq(RateLimitsConfig.class)))
                .thenReturn(rateLimits);
        when(objectMapper.readValue(eq("{\"maxRequestsPerMonth\":1000000}"), eq(QuotaConfig.class)))
                .thenReturn(quota);

        PlanResponse response = planService.createPlan(request);

        assertThat(response.getId()).isEqualTo(planId);
        assertThat(response.getName()).isEqualTo("Gold Plan");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getPricingModel()).isEqualTo("FLAT_RATE");
        assertThat(response.getPriceAmount()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(response.getRequestsPerSecond()).isEqualTo(100);
        assertThat(response.getMaxRequestsPerMonth()).isEqualTo(1000000L);
        verify(planRepository).save(any(PlanEntity.class));
    }

    @Test
    void shouldListPlans() {
        PlanEntity p1 = PlanEntity.builder().id(UUID.randomUUID()).name("Free").status("ACTIVE").build();
        PlanEntity p2 = PlanEntity.builder().id(UUID.randomUUID()).name("Pro").status("ACTIVE").build();

        when(planRepository.findAll()).thenReturn(List.of(p1, p2));

        List<PlanResponse> result = planService.listPlans();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlanResponse::getName).containsExactly("Free", "Pro");
    }

    @Test
    void shouldGetPlan() {
        UUID id = UUID.randomUUID();
        PlanEntity entity = PlanEntity.builder()
                .id(id).name("Basic").status("ACTIVE").build();

        when(planRepository.findById(id)).thenReturn(Optional.of(entity));

        PlanResponse response = planService.getPlan(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("Basic");
    }

    @Test
    void shouldUpdatePlan() throws Exception {
        UUID id = UUID.randomUUID();
        PlanEntity existing = PlanEntity.builder()
                .id(id).name("Old Plan").description("Old desc")
                .status("ACTIVE").enforcement(Enforcement.SOFT)
                .pricingModel("FREE").build();

        CreatePlanRequest request = CreatePlanRequest.builder()
                .name("Updated Plan")
                .pricingModel("FLAT_RATE")
                .priceAmount(new BigDecimal("29.99"))
                .build();

        when(planRepository.findById(id)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanResponse response = planService.updatePlan(id, request);

        assertThat(response.getName()).isEqualTo("Updated Plan");
        assertThat(response.getPricingModel()).isEqualTo("FLAT_RATE");
        assertThat(response.getPriceAmount()).isEqualByComparingTo(new BigDecimal("29.99"));
        // description should remain unchanged
        assertThat(response.getDescription()).isEqualTo("Old desc");
    }

    @Test
    void shouldDeletePlan() {
        UUID id = UUID.randomUUID();
        PlanEntity entity = PlanEntity.builder().id(id).name("To Delete").status("ACTIVE").build();

        when(planRepository.findById(id)).thenReturn(Optional.of(entity));

        planService.deletePlan(id);

        verify(planRepository).delete(entity);
    }
}
