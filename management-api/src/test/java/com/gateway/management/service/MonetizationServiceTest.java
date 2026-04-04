package com.gateway.management.service;

import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.PlanEntity;
import com.gateway.management.repository.InvoiceRepository;
import com.gateway.management.repository.PaymentMethodRepository;
import com.gateway.management.repository.PlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonetizationServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private MonetizationService monetizationService;

    private Query mockRequestCountQuery(long count) {
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(count);
        return query;
    }

    private Query mockPlanResolutionQuery(PlanEntity plan) {
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        if (plan != null) {
            when(query.getResultList()).thenReturn(Collections.singletonList(plan));
        } else {
            when(query.getResultList()).thenReturn(Collections.emptyList());
        }
        return query;
    }

    private void stubEntityManager(long requestCount, PlanEntity plan) {
        Query countQuery = mockRequestCountQuery(requestCount);
        Query planQuery = mockPlanResolutionQuery(plan);

        // First createNativeQuery call is for request count, second is for plan resolution
        when(entityManager.createNativeQuery(contains("request_logs")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("gateway.plans"), eq(PlanEntity.class)))
                .thenReturn(planQuery);
    }

    @Test
    void shouldGenerateInvoiceForFreePlan() {
        UUID consumerId = UUID.randomUUID();
        PlanEntity plan = PlanEntity.builder()
                .id(UUID.randomUUID()).name("Free").pricingModel("FREE").currency("USD").build();

        stubEntityManager(500L, plan);
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenAnswer(inv -> {
            InvoiceEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        InvoiceEntity invoice = monetizationService.generateInvoice(consumerId, "2026-03");

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getCurrency()).isEqualTo("USD");
        assertThat(invoice.getStatus()).isEqualTo("DRAFT");
        verify(invoiceRepository).save(any(InvoiceEntity.class));
    }

    @Test
    void shouldGenerateInvoiceForFlatRate() {
        UUID consumerId = UUID.randomUUID();
        PlanEntity plan = PlanEntity.builder()
                .id(UUID.randomUUID()).name("Pro")
                .pricingModel("FLAT_RATE")
                .priceAmount(new BigDecimal("49.99"))
                .currency("USD")
                .build();

        stubEntityManager(10000L, plan);
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenAnswer(inv -> {
            InvoiceEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        InvoiceEntity invoice = monetizationService.generateInvoice(consumerId, "2026-03");

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    void shouldGenerateInvoiceForPayPerUse() {
        UUID consumerId = UUID.randomUUID();
        PlanEntity plan = PlanEntity.builder()
                .id(UUID.randomUUID()).name("Pay Per Use")
                .pricingModel("PAY_PER_USE")
                .overageRate(new BigDecimal("0.001"))
                .currency("USD")
                .build();

        stubEntityManager(5000L, plan);
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenAnswer(inv -> {
            InvoiceEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        InvoiceEntity invoice = monetizationService.generateInvoice(consumerId, "2026-03");

        // 5000 * 0.001 = 5.00
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void shouldGenerateInvoiceForTieredWithOverage() {
        UUID consumerId = UUID.randomUUID();
        PlanEntity plan = PlanEntity.builder()
                .id(UUID.randomUUID()).name("Enterprise")
                .pricingModel("TIERED")
                .priceAmount(new BigDecimal("100.00"))
                .includedRequests(10000L)
                .overageRate(new BigDecimal("0.005"))
                .currency("USD")
                .build();

        stubEntityManager(15000L, plan);
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenAnswer(inv -> {
            InvoiceEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        InvoiceEntity invoice = monetizationService.generateInvoice(consumerId, "2026-03");

        // Base: 100.00 + Overage: (15000-10000) * 0.005 = 25.00 => Total: 125.00
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
    }

    @Test
    void shouldGenerateInvoiceForTieredWithinIncluded() {
        UUID consumerId = UUID.randomUUID();
        PlanEntity plan = PlanEntity.builder()
                .id(UUID.randomUUID()).name("Enterprise")
                .pricingModel("TIERED")
                .priceAmount(new BigDecimal("100.00"))
                .includedRequests(10000L)
                .overageRate(new BigDecimal("0.005"))
                .currency("USD")
                .build();

        stubEntityManager(5000L, plan);
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenAnswer(inv -> {
            InvoiceEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        InvoiceEntity invoice = monetizationService.generateInvoice(consumerId, "2026-03");

        // No overage: just the base fee
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
