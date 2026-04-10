package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.config.StripeConfig;
import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import com.gateway.management.repository.PaymentGatewaySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {

    @Qualifier("stripeRestClient")
    private final RestClient stripeRestClient;
    private final StripeConfig stripeConfig;
    private final PaymentGatewaySettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    private PaymentGatewaySettingsEntity getSettings() {
        return stripeConfig.resolveSettings(settingsRepository);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createCheckoutSession(String email, BigDecimal amount,
                                                      String currency, String reference,
                                                      UUID invoiceId, String callbackUrl) {
        PaymentGatewaySettingsEntity settings = getSettings();
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        try {
            String formBody = "mode=payment"
                    + "&success_url=" + java.net.URLEncoder.encode(callbackUrl + "?verify=true&reference=" + reference + "&provider=stripe", StandardCharsets.UTF_8)
                    + "&cancel_url=" + java.net.URLEncoder.encode(callbackUrl + "?cancelled=true", StandardCharsets.UTF_8)
                    + "&customer_email=" + java.net.URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&client_reference_id=" + reference
                    + "&line_items[0][price_data][currency]=" + currency.toLowerCase()
                    + "&line_items[0][price_data][unit_amount]=" + amountInSmallestUnit
                    + "&line_items[0][price_data][product_data][name]=" + java.net.URLEncoder.encode("Invoice " + (invoiceId != null ? invoiceId.toString().substring(0, 8) : "payment"), StandardCharsets.UTF_8)
                    + "&line_items[0][quantity]=1"
                    + "&metadata[invoiceId]=" + (invoiceId != null ? invoiceId.toString() : "")
                    + "&metadata[reference]=" + reference;

            String responseBody = stripeRestClient.post()
                    .uri(settings.getBaseUrl() + "/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            log.info("Stripe checkout session created - id: {}, url: {}", response.get("id"), response.get("url"));
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Stripe createCheckoutSession failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Stripe error: " + extractStripeMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Stripe checkout session: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> retrieveSession(String sessionId) {
        PaymentGatewaySettingsEntity settings = getSettings();
        try {
            String responseBody = stripeRestClient.get()
                    .uri(settings.getBaseUrl() + "/v1/checkout/sessions/" + sessionId)
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readValue(responseBody, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Stripe retrieveSession failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Stripe verification failed: " + extractStripeMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve Stripe session: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createPaymentIntent(BigDecimal amount, String currency,
                                                     String customerToken, String paymentMethodId,
                                                     String reference) {
        PaymentGatewaySettingsEntity settings = getSettings();
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        try {
            String formBody = "amount=" + amountInSmallestUnit
                    + "&currency=" + currency.toLowerCase()
                    + "&customer=" + customerToken
                    + "&payment_method=" + paymentMethodId
                    + "&confirm=true"
                    + "&off_session=true"
                    + "&metadata[reference]=" + reference;

            String responseBody = stripeRestClient.post()
                    .uri(settings.getBaseUrl() + "/v1/payment_intents")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            log.info("Stripe payment intent created - id: {}, status: {}", response.get("id"), response.get("status"));
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Stripe createPaymentIntent failed: {}", e.getResponseBodyAsString());
            throw new IllegalStateException("Stripe charge failed: " + extractStripeMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Stripe payment intent: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createRefund(String paymentIntentId, BigDecimal amount) {
        PaymentGatewaySettingsEntity settings = getSettings();
        try {
            String formBody = "payment_intent=" + paymentIntentId;
            if (amount != null) {
                formBody += "&amount=" + amount.multiply(BigDecimal.valueOf(100)).longValueExact();
            }

            String responseBody = stripeRestClient.post()
                    .uri(settings.getBaseUrl() + "/v1/refunds")
                    .header("Authorization", "Bearer " + settings.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            log.info("Stripe refund created - id: {}, status: {}", response.get("id"), response.get("status"));
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Stripe refund failed: {}", e.getResponseBodyAsString());
            throw new IllegalStateException("Stripe refund failed: " + extractStripeMessage(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Stripe refund: " + e.getMessage(), e);
        }
    }

    public boolean validateWebhookSignature(String payload, String sigHeader) {
        try {
            PaymentGatewaySettingsEntity settings = getSettings();
            String webhookSecret = settings.getExtraConfig() != null ? extractWebhookSecret(settings.getExtraConfig()) : stripeConfig.getWebhookSecret();
            if (webhookSecret == null) return false;

            String[] parts = sigHeader.split(",");
            String timestamp = null;
            String signature = null;
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if ("t".equals(kv[0].trim())) timestamp = kv[1];
                if ("v1".equals(kv[0].trim())) signature = kv[1];
            }
            if (timestamp == null || signature == null) return false;

            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computed = java.util.HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Stripe webhook signature validation failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractStripeMessage(String responseBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            if (error != null && error.get("message") != null) return error.get("message").toString();
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractWebhookSecret(String extraConfig) {
        try {
            Map<String, Object> config = objectMapper.readValue(extraConfig, Map.class);
            return config.get("webhookSecret") != null ? config.get("webhookSecret").toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
