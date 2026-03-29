package com.gateway.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.paystack.PaystackWebhookEvent;
import com.gateway.management.service.PaymentFlowService;
import com.gateway.management.service.PaystackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks/paystack")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final PaymentFlowService paymentFlowService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (signature == null || !paystackService.validateWebhookSignature(payload, signature)) {
            log.warn("Invalid Paystack webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            PaystackWebhookEvent event = objectMapper.readValue(payload, PaystackWebhookEvent.class);
            log.info("Paystack webhook received: event={}", event.getEvent());
            paymentFlowService.handleWebhookEvent(event.getEvent(), event.getData());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Failed to process Paystack webhook", e);
            return ResponseEntity.ok("OK"); // Return 200 to prevent Paystack retries on processing errors
        }
    }
}
