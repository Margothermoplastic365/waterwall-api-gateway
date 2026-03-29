package com.gateway.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.config.NotificationChannelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Delivers notifications to a generic HTTP endpoint as a JSON POST.
 * <p>
 * The payload is signed with HMAC-SHA256 and the hex-encoded signature is sent
 * in the {@code X-Webhook-Signature} header (prefixed with {@code sha256=}).
 * <p>
 * Additional static headers can be configured via
 * {@code gateway.notifications.channels.webhook.headers} in application.yml.
 * <p>
 * Retry policy: configurable (default 3 attempts) with exponential backoff.
 */
@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationChannel.class);

    private static final long BASE_DELAY_MS = 1_000;

    private final NotificationChannelProperties.WebhookProperties config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public WebhookNotificationChannel(NotificationChannelProperties properties,
                                      ObjectMapper objectMapper) {
        this.config = properties.getWebhook();
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String channelName() {
        return "webhook";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled()
                && config.getUrl() != null
                && !config.getUrl().isBlank();
    }

    @Override
    public void send(String title, String message, String severity, Map<String, Object> metadata) {
        if (!isEnabled()) {
            log.debug("Webhook channel is disabled; skipping notification");
            return;
        }

        String jsonPayload = buildPayload(title, message, severity, metadata);
        if (jsonPayload == null) {
            return; // serialisation failure already logged
        }

        String signature = computeHmacSha256(jsonPayload, config.getSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Signature", "sha256=" + signature);

        // Apply any extra static headers from configuration
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(headers::set);
        }

        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

        int maxRetries = config.getMaxRetries() > 0 ? config.getMaxRetries() : 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                restTemplate.postForEntity(config.getUrl(), request, String.class);
                log.info("Webhook notification sent: title='{}' url='{}' attempt={}",
                        title, config.getUrl(), attempt);
                return;
            } catch (Exception ex) {
                log.warn("Webhook delivery failed: url='{}' attempt={}/{} error={}",
                        config.getUrl(), attempt, maxRetries, ex.getMessage());
                if (attempt < maxRetries) {
                    sleepBackoff(attempt);
                } else {
                    log.error("Webhook delivery exhausted all retries for url='{}'", config.getUrl());
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String buildPayload(String title, String message, String severity,
                                Map<String, Object> metadata) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("title", title);
            body.put("message", message);
            body.put("severity", severity);
            body.put("timestamp", Instant.now().toString());
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            log.error("Failed to serialise webhook payload: {}", ex.getMessage());
            return null;
        }
    }

    private static String computeHmacSha256(String data, String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            LoggerFactory.getLogger(WebhookNotificationChannel.class)
                    .error("Failed to compute HMAC-SHA256: {}", e.getMessage());
            return "";
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
