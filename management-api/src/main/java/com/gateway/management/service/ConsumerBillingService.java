package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.AddPaymentMethodRequest;
import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.PaymentMethodEntity;
import com.gateway.management.repository.InvoiceRepository;
import com.gateway.management.repository.PaymentMethodRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerBillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    // ── Invoices ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceEntity> listInvoices() {
        UUID consumerId = resolveConsumerId();
        log.debug("Listing invoices for consumer={}", consumerId);
        return invoiceRepository.findByConsumerId(consumerId);
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getInvoice(UUID invoiceId) {
        UUID consumerId = resolveConsumerId();
        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));
        validateOwnership(invoice.getConsumerId(), consumerId, "Invoice");
        return invoice;
    }

    // ── Payment Methods ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentMethodEntity> listPaymentMethods() {
        UUID consumerId = resolveConsumerId();
        log.debug("Listing payment methods for consumer={}", consumerId);
        return paymentMethodRepository.findByConsumerId(consumerId);
    }

    @Transactional
    public PaymentMethodEntity addPaymentMethod(AddPaymentMethodRequest request) {
        UUID consumerId = resolveConsumerId();

        // If this is the first payment method, make it the default
        List<PaymentMethodEntity> existing = paymentMethodRepository.findByConsumerId(consumerId);
        boolean shouldBeDefault = existing.isEmpty();

        PaymentMethodEntity entity = PaymentMethodEntity.builder()
                .consumerId(consumerId)
                .type(request.getType())
                .provider(request.getProvider())
                .providerRef(request.getProviderRef())
                .isDefault(shouldBeDefault)
                .build();

        entity = paymentMethodRepository.save(entity);
        log.info("Added payment method: id={} consumer={} type={} provider={}",
                entity.getId(), consumerId, entity.getType(), entity.getProvider());
        return entity;
    }

    @Transactional
    public void removePaymentMethod(UUID paymentMethodId) {
        UUID consumerId = resolveConsumerId();
        PaymentMethodEntity entity = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new EntityNotFoundException("Payment method not found: " + paymentMethodId));
        validateOwnership(entity.getConsumerId(), consumerId, "Payment method");

        paymentMethodRepository.delete(entity);
        log.info("Removed payment method: id={} consumer={}", paymentMethodId, consumerId);

        // If the removed method was the default, promote the first remaining method
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            List<PaymentMethodEntity> remaining = paymentMethodRepository.findByConsumerId(consumerId);
            if (!remaining.isEmpty()) {
                PaymentMethodEntity newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                paymentMethodRepository.save(newDefault);
                log.info("Promoted payment method id={} to default for consumer={}", newDefault.getId(), consumerId);
            }
        }
    }

    @Transactional
    public PaymentMethodEntity setDefaultPaymentMethod(UUID paymentMethodId) {
        UUID consumerId = resolveConsumerId();
        PaymentMethodEntity target = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new EntityNotFoundException("Payment method not found: " + paymentMethodId));
        validateOwnership(target.getConsumerId(), consumerId, "Payment method");

        // Clear the current default(s) for this consumer
        List<PaymentMethodEntity> allMethods = paymentMethodRepository.findByConsumerId(consumerId);
        for (PaymentMethodEntity method : allMethods) {
            if (Boolean.TRUE.equals(method.getIsDefault())) {
                method.setIsDefault(false);
                paymentMethodRepository.save(method);
            }
        }

        // Set the new default
        target.setIsDefault(true);
        target = paymentMethodRepository.save(target);
        log.info("Set payment method id={} as default for consumer={}", paymentMethodId, consumerId);
        return target;
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private UUID resolveConsumerId() {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated consumer found in security context");
        }
        return UUID.fromString(userId);
    }

    private void validateOwnership(UUID resourceConsumerId, UUID currentConsumerId, String resourceType) {
        if (!resourceConsumerId.equals(currentConsumerId)) {
            throw new SecurityException(resourceType + " does not belong to the current user");
        }
    }
}
