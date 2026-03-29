package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.PaymentInitResponse;
import com.gateway.management.dto.paystack.PaystackInitializeResponse;
import com.gateway.management.dto.paystack.PaystackVerifyResponse;
import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.PaymentMethodEntity;
import com.gateway.management.repository.InvoiceRepository;
import com.gateway.management.repository.PaymentMethodRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentFlowService {

    private final PaystackService paystackService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> PAYABLE_STATUSES = Set.of("DRAFT", "SENT", "OVERDUE");

    @Transactional
    public PaymentInitResponse initiateInvoicePayment(UUID invoiceId) {
        String userId = SecurityContextHelper.getCurrentUserId();
        String email = SecurityContextHelper.getCurrentEmail();

        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));

        if (!invoice.getConsumerId().toString().equals(userId)) {
            throw new SecurityException("User does not own this invoice");
        }

        if ("PAID".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is already paid");
        }

        if (!PAYABLE_STATUSES.contains(invoice.getStatus())) {
            throw new IllegalStateException("Invoice status does not allow payment: " + invoice.getStatus());
        }

        // If invoice already has a paystack reference, verify it first
        if (invoice.getPaystackReference() != null) {
            try {
                PaystackVerifyResponse existingVerification =
                        paystackService.verifyTransaction(invoice.getPaystackReference());
                if ("success".equals(existingVerification.getData().getStatus())) {
                    invoice.setStatus("PAID");
                    invoice.setPaidAt(Instant.now());
                    invoiceRepository.save(invoice);
                    return new PaymentInitResponse(null, invoice.getPaystackReference(), null);
                }
            } catch (Exception e) {
                log.warn("Failed to verify existing reference {}, generating new one",
                        invoice.getPaystackReference(), e);
            }
        }

        String reference = "INV-" + invoiceId.toString().substring(0, 8) + "-" + System.currentTimeMillis();

        PaystackInitializeResponse response = paystackService.initializeTransaction(
                email, invoice.getTotalAmount(), invoice.getCurrency(), reference, invoiceId);

        invoice.setPaystackReference(reference);
        invoice.setStatus("PENDING");
        invoiceRepository.save(invoice);

        return new PaymentInitResponse(
                response.getData().getAuthorization_url(),
                reference,
                response.getData().getAccess_code()
        );
    }

    @Transactional
    public InvoiceEntity verifyPayment(String reference) {
        PaystackVerifyResponse response = paystackService.verifyTransaction(reference);

        if (!"success".equals(response.getData().getStatus())) {
            throw new IllegalStateException("Payment not successful. Status: " + response.getData().getStatus());
        }

        InvoiceEntity invoice = invoiceRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for reference: " + reference));

        invoice.setStatus("PAID");
        invoice.setPaidAt(Instant.now());

        // Extract authorization data and save payment method
        PaystackVerifyResponse.PaystackAuthorization auth = response.getData().getAuthorization();
        PaystackVerifyResponse.PaystackCustomer customer = response.getData().getCustomer();

        if (auth != null) {
            PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder()
                    .consumerId(invoice.getConsumerId())
                    .type(auth.getCard_type() != null ? auth.getCard_type() : "card")
                    .provider("paystack")
                    .providerRef(response.getData().getReference())
                    .isDefault(true)
                    .paystackAuthorizationCode(auth.getAuthorization_code())
                    .paystackCustomerCode(customer != null ? customer.getCustomer_code() : null)
                    .cardLast4(auth.getLast4())
                    .cardBrand(auth.getBrand())
                    .build();
            PaymentMethodEntity saved = paymentMethodRepository.save(paymentMethod);
            invoice.setPaymentMethodId(saved.getId());
        }

        return invoiceRepository.save(invoice);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhookEvent(String eventType, Map<String, Object> data) {
        if ("charge.success".equals(eventType)) {
            String reference = (String) data.get("reference");
            log.info("Processing charge.success webhook for reference: {}", reference);

            invoiceRepository.findByPaystackReference(reference).ifPresent(invoice -> {
                if (!"PAID".equals(invoice.getStatus())) {
                    invoice.setStatus("PAID");
                    invoice.setPaidAt(Instant.now());

                    // Extract authorization info from webhook data
                    Map<String, Object> authData = (Map<String, Object>) data.get("authorization");
                    if (authData != null) {
                        String authCode = (String) authData.get("authorization_code");
                        String last4 = (String) authData.get("last4");
                        String brand = (String) authData.get("brand");
                        String cardType = (String) authData.get("card_type");

                        Map<String, Object> customerData = (Map<String, Object>) data.get("customer");
                        String customerCode = customerData != null
                                ? (String) customerData.get("customer_code") : null;

                        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder()
                                .consumerId(invoice.getConsumerId())
                                .type(cardType != null ? cardType : "card")
                                .provider("paystack")
                                .providerRef(reference)
                                .isDefault(true)
                                .paystackAuthorizationCode(authCode)
                                .paystackCustomerCode(customerCode)
                                .cardLast4(last4)
                                .cardBrand(brand)
                                .build();
                        PaymentMethodEntity saved = paymentMethodRepository.save(paymentMethod);
                        invoice.setPaymentMethodId(saved.getId());
                    }

                    invoiceRepository.save(invoice);
                    log.info("Invoice {} marked as PAID via webhook", invoice.getId());
                }
            });
        } else {
            log.info("Unhandled webhook event type: {}", eventType);
        }
    }
}
