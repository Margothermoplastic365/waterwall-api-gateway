package com.gateway.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.entity.WebhookDeliveryLogEntity;
import com.gateway.notification.entity.WebhookEndpointEntity;
import com.gateway.notification.repository.WebhookDeliveryLogRepository;
import com.gateway.notification.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Delivers webhook notifications to user-registered HTTP endpoints.
 * Signs each payload with HMAC-SHA256 using the endpoint's secret.
 * Retries failed deliveries up to 3 times with exponential backoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryLogRepository webhookDeliveryLogRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Deliver a webhook event to all active endpoints registered by the given user.
     *
     * @param userId    the user whose webhooks to fire
     * @param eventType the type of event (used in payload)
     * @param payload   the event payload object
     */
    public void deliverWebhook(String userId, String eventType, Object payload) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId for webhook delivery: {}", userId);
            return;
        }

        List<WebhookEndpointEntity> endpoints = webhookEndpointRepository.findByUserIdAndActiveTrue(userUuid);

        if (endpoints.isEmpty()) {
            log.debug("No active webhook endpoints for userId={}", userId);
            return;
        }

        for (WebhookEndpointEntity endpoint : endpoints) {
            deliverToEndpoint(endpoint, eventType, payload);
        }
    }

    private void deliverToEndpoint(WebhookEndpointEntity endpoint, String eventType, Object payload) {
        String jsonPayload;
        try {
            var webhookBody = new WebhookPayload(eventType, payload);
            jsonPayload = objectMapper.writeValueAsString(webhookBody);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for endpoint={}: {}", endpoint.getId(), e.getMessage());
            return;
        }

        String signature = computeHmacSha256(jsonPayload, endpoint.getSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Signature", "sha256=" + signature);
        headers.set("X-Gateway-Event", eventType);

        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint.getUrl(), HttpMethod.POST, request, String.class);

                log.info("Webhook delivered: endpoint={} url={} status={} attempt={}",
                        endpoint.getId(), endpoint.getUrl(), response.getStatusCode(), attempt);

                saveDeliveryLog(endpoint.getId(), eventType, response.getStatusCode().value(),
                        true, null, attempt);
                return; // success
            } catch (HttpStatusCodeException e) {
                log.warn("Webhook delivery failed: endpoint={} url={} attempt={}/{} status={} error={}",
                        endpoint.getId(), endpoint.getUrl(), attempt, MAX_RETRIES,
                        e.getStatusCode().value(), e.getMessage());

                saveDeliveryLog(endpoint.getId(), eventType, e.getStatusCode().value(),
                        false, e.getMessage(), attempt);

                if (attempt < MAX_RETRIES) {
                    sleepBeforeRetry(attempt);
                } else {
                    log.error("Webhook delivery exhausted all retries: endpoint={} url={}",
                            endpoint.getId(), endpoint.getUrl());
                }
            } catch (Exception e) {
                log.warn("Webhook delivery failed: endpoint={} url={} attempt={}/{} error={}",
                        endpoint.getId(), endpoint.getUrl(), attempt, MAX_RETRIES, e.getMessage());

                saveDeliveryLog(endpoint.getId(), eventType, 0,
                        false, e.getMessage(), attempt);

                if (attempt < MAX_RETRIES) {
                    sleepBeforeRetry(attempt);
                } else {
                    log.error("Webhook delivery exhausted all retries: endpoint={} url={}",
                            endpoint.getId(), endpoint.getUrl());
                }
            }
        }
    }

    private void saveDeliveryLog(UUID endpointId, String eventType, int statusCode,
                                  boolean success, String errorMessage, int attemptNumber) {
        try {
            WebhookDeliveryLogEntity logEntity = WebhookDeliveryLogEntity.builder()
                    .webhookEndpointId(endpointId)
                    .eventType(eventType)
                    .statusCode(statusCode)
                    .success(success)
                    .errorMessage(errorMessage)
                    .attemptNumber(attemptNumber)
                    .deliveredAt(Instant.now())
                    .build();
            webhookDeliveryLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("Failed to save webhook delivery log for endpoint={}: {}", endpointId, e.getMessage());
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute HMAC-SHA256: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Simple wrapper for the webhook HTTP body.
     */
    private record WebhookPayload(String eventType, Object data) {}
}
