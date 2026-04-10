package com.gateway.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.service.PaymentFlowService;
import com.gateway.management.service.payment.StripePaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripePaymentProvider stripePaymentProvider;
    private final PaymentFlowService paymentFlowService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (signature == null || !stripePaymentProvider.validateWebhook(payload, signature)) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            String eventType = (String) event.get("type");
            Map<String, Object> data = (Map<String, Object>) event.get("data");
            Map<String, Object> object = data != null ? (Map<String, Object>) data.get("object") : null;

            if (object == null) {
                return ResponseEntity.ok("OK");
            }

            log.info("Stripe webhook received: type={}", eventType);

            String normalizedType = switch (eventType) {
                case "checkout.session.completed", "payment_intent.succeeded" -> "charge.success";
                case "payment_intent.payment_failed" -> "charge.failed";
                default -> eventType;
            };

            Map<String, Object> metadata = object.get("metadata") != null
                    ? (Map<String, Object>) object.get("metadata") : Map.of();
            String reference = metadata.get("reference") != null
                    ? metadata.get("reference").toString()
                    : (String) object.get("client_reference_id");

            if (reference != null) {
                object.put("reference", reference);
                paymentFlowService.handleWebhookEvent(normalizedType, object);
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            return ResponseEntity.ok("OK");
        }
    }
}
