package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.config.PaystackConfig;
import com.gateway.management.dto.paystack.PaystackInitializeResponse;
import com.gateway.management.dto.paystack.PaystackVerifyResponse;
import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import com.gateway.management.repository.PaymentGatewaySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaystackService {

    @Qualifier("paystackRestClient")
    private final RestClient paystackRestClient;
    private final PaystackConfig paystackConfig;
    private final PaymentGatewaySettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    private PaymentGatewaySettingsEntity getSettings() {
        return paystackConfig.resolveSettings(settingsRepository);
    }

    public PaystackInitializeResponse initializeTransaction(String email, BigDecimal amount,
                                                            String currency, String reference,
                                                            UUID invoiceId) {
        PaymentGatewaySettingsEntity settings = getSettings();
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("amount", amountInSmallestUnit);
        requestBody.put("email", email);
        requestBody.put("reference", reference);
        requestBody.put("currency", currency);
        requestBody.put("callback_url", settings.getCallbackUrl());
        requestBody.put("metadata", Map.of("invoiceId", invoiceId != null ? invoiceId.toString() : "", "reference", reference));

        try {
            String responseBody = paystackRestClient.post()
                    .uri(settings.getBaseUrl() + "/transaction/initialize")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            PaystackInitializeResponse response = objectMapper.readValue(responseBody,
                    PaystackInitializeResponse.class);
            log.info("Paystack transaction initialized - reference: {}, authorization_url: {}",
                    reference, response.getData().getAuthorization_url());
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Paystack initialize failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Payment provider error: " + extractPaystackMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize payment: " + e.getMessage(), e);
        }
    }

    public PaystackVerifyResponse verifyTransaction(String reference) {
        PaymentGatewaySettingsEntity settings = getSettings();

        try {
            String responseBody = paystackRestClient.get()
                    .uri(settings.getBaseUrl() + "/transaction/verify/{reference}", reference)
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .retrieve()
                    .body(String.class);

            PaystackVerifyResponse response = objectMapper.readValue(responseBody,
                    PaystackVerifyResponse.class);
            log.info("Paystack transaction verified - reference: {}, status: {}",
                    reference, response.getData().getStatus());
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Paystack verify failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Payment verification failed: " + extractPaystackMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify payment: " + e.getMessage(), e);
        }
    }

    public PaystackVerifyResponse chargeAuthorization(String authorizationCode, String email,
                                                       BigDecimal amount, String currency,
                                                       String reference) {
        PaymentGatewaySettingsEntity settings = getSettings();
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("authorization_code", authorizationCode);
        requestBody.put("email", email);
        requestBody.put("amount", amountInSmallestUnit);
        requestBody.put("currency", currency);
        requestBody.put("reference", reference);

        try {
            String responseBody = paystackRestClient.post()
                    .uri(settings.getBaseUrl() + "/transaction/charge_authorization")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            PaystackVerifyResponse response = objectMapper.readValue(responseBody, PaystackVerifyResponse.class);
            log.info("Paystack charge_authorization - reference: {}, status: {}", reference, response.getData().getStatus());
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Paystack charge_authorization failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Charge failed: " + extractPaystackMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to charge authorization: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> refundTransaction(String reference, BigDecimal amount) {
        PaymentGatewaySettingsEntity settings = getSettings();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transaction", reference);
        if (amount != null) {
            requestBody.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValueExact());
        }

        try {
            String responseBody = paystackRestClient.post()
                    .uri(settings.getBaseUrl() + "/refund")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            log.info("Paystack refund - reference: {}, response status: {}", reference, response.get("status"));
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Paystack refund failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Refund failed: " + extractPaystackMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process refund: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractPaystackMessage(String responseBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(responseBody, Map.class);
            String message = (String) body.get("message");
            return message != null ? message : responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    public boolean validateWebhookSignature(String payload, String signature) {
        try {
            PaymentGatewaySettingsEntity settings = getSettings();
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    settings.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hmacBytes);
            return computedSignature.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Failed to validate webhook signature", e);
            return false;
        }
    }
}
