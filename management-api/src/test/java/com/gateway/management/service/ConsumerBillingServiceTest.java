package com.gateway.management.service;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.management.dto.AddPaymentMethodRequest;
import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.PaymentMethodEntity;
import com.gateway.management.repository.InvoiceRepository;
import com.gateway.management.repository.PaymentMethodRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumerBillingServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @InjectMocks
    private ConsumerBillingService consumerBillingService;

    private final UUID consumerId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                consumerId.toString(), null, "consumer@example.com",
                List.of("CONSUMER"), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListInvoices() {
        InvoiceEntity invoice1 = InvoiceEntity.builder()
                .id(UUID.randomUUID()).consumerId(consumerId)
                .totalAmount(new BigDecimal("49.99")).currency("USD")
                .billingPeriodStart(LocalDate.of(2026, 3, 1))
                .billingPeriodEnd(LocalDate.of(2026, 3, 31))
                .status("PAID").build();
        InvoiceEntity invoice2 = InvoiceEntity.builder()
                .id(UUID.randomUUID()).consumerId(consumerId)
                .totalAmount(new BigDecimal("29.99")).currency("USD")
                .billingPeriodStart(LocalDate.of(2026, 2, 1))
                .billingPeriodEnd(LocalDate.of(2026, 2, 28))
                .status("DRAFT").build();

        when(invoiceRepository.findByConsumerId(consumerId)).thenReturn(List.of(invoice1, invoice2));

        List<InvoiceEntity> invoices = consumerBillingService.listInvoices();

        assertThat(invoices).hasSize(2);
        verify(invoiceRepository).findByConsumerId(consumerId);
    }

    @Test
    void shouldAddPaymentMethodAsDefault() {
        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder()
                .type("CARD").provider("stripe").providerRef("tok_abc123").build();

        when(paymentMethodRepository.findByConsumerId(consumerId)).thenReturn(Collections.emptyList());
        when(paymentMethodRepository.save(any(PaymentMethodEntity.class)))
                .thenAnswer(inv -> {
                    PaymentMethodEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        PaymentMethodEntity result = consumerBillingService.addPaymentMethod(request);

        assertThat(result.getIsDefault()).isTrue();
        assertThat(result.getType()).isEqualTo("CARD");
        assertThat(result.getProvider()).isEqualTo("stripe");
        assertThat(result.getConsumerId()).isEqualTo(consumerId);
    }

    @Test
    void shouldAddPaymentMethodNotDefaultWhenExisting() {
        PaymentMethodEntity existing = PaymentMethodEntity.builder()
                .id(UUID.randomUUID()).consumerId(consumerId).type("CARD")
                .isDefault(true).build();

        AddPaymentMethodRequest request = AddPaymentMethodRequest.builder()
                .type("BANK").provider("paystack").providerRef("ba_xyz789").build();

        when(paymentMethodRepository.findByConsumerId(consumerId)).thenReturn(List.of(existing));
        when(paymentMethodRepository.save(any(PaymentMethodEntity.class)))
                .thenAnswer(inv -> {
                    PaymentMethodEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        PaymentMethodEntity result = consumerBillingService.addPaymentMethod(request);

        assertThat(result.getIsDefault()).isFalse();
    }

    @Test
    void shouldRemovePaymentMethod() {
        UUID pmId = UUID.randomUUID();
        PaymentMethodEntity pm = PaymentMethodEntity.builder()
                .id(pmId).consumerId(consumerId).type("CARD").isDefault(false).build();

        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(pm));

        consumerBillingService.removePaymentMethod(pmId);

        verify(paymentMethodRepository).delete(pm);
    }

    @Test
    void shouldRemovePaymentMethodAndPromoteDefault() {
        UUID pmId = UUID.randomUUID();
        PaymentMethodEntity defaultPm = PaymentMethodEntity.builder()
                .id(pmId).consumerId(consumerId).type("CARD").isDefault(true).build();

        UUID otherId = UUID.randomUUID();
        PaymentMethodEntity otherPm = PaymentMethodEntity.builder()
                .id(otherId).consumerId(consumerId).type("BANK").isDefault(false).build();

        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(defaultPm));
        when(paymentMethodRepository.findByConsumerId(consumerId)).thenReturn(List.of(otherPm));
        when(paymentMethodRepository.save(any(PaymentMethodEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        consumerBillingService.removePaymentMethod(pmId);

        verify(paymentMethodRepository).delete(defaultPm);
        ArgumentCaptor<PaymentMethodEntity> captor = ArgumentCaptor.forClass(PaymentMethodEntity.class);
        verify(paymentMethodRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isTrue();
        assertThat(captor.getValue().getId()).isEqualTo(otherId);
    }

    @Test
    void shouldRemovePaymentMethodValidateOwnership() {
        UUID pmId = UUID.randomUUID();
        UUID otherConsumer = UUID.randomUUID();
        PaymentMethodEntity pm = PaymentMethodEntity.builder()
                .id(pmId).consumerId(otherConsumer).type("CARD").isDefault(false).build();

        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(pm));

        assertThatThrownBy(() -> consumerBillingService.removePaymentMethod(pmId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void shouldSetDefaultPaymentMethod() {
        UUID targetId = UUID.randomUUID();
        UUID currentDefaultId = UUID.randomUUID();

        PaymentMethodEntity target = PaymentMethodEntity.builder()
                .id(targetId).consumerId(consumerId).type("BANK").isDefault(false).build();
        PaymentMethodEntity currentDefault = PaymentMethodEntity.builder()
                .id(currentDefaultId).consumerId(consumerId).type("CARD").isDefault(true).build();

        when(paymentMethodRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(paymentMethodRepository.findByConsumerId(consumerId))
                .thenReturn(List.of(target, currentDefault));
        when(paymentMethodRepository.save(any(PaymentMethodEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentMethodEntity result = consumerBillingService.setDefaultPaymentMethod(targetId);

        assertThat(result.getIsDefault()).isTrue();
        // Verify the old default was cleared
        verify(paymentMethodRepository, atLeast(2)).save(any(PaymentMethodEntity.class));
    }
}
