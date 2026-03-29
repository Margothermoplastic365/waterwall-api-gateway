package com.gateway.notification.channel;

import com.gateway.notification.config.NotificationChannelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Posts alert-style messages to a Microsoft Teams incoming webhook using the
 * Adaptive Card format.
 * <p>
 * Retry policy: 3 attempts with exponential backoff (1 s, 2 s, 4 s).
 */
@Component
public class TeamsNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TeamsNotificationChannel.class);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1_000;

    private final NotificationChannelProperties.TeamsProperties config;
    private final RestTemplate restTemplate;

    public TeamsNotificationChannel(NotificationChannelProperties properties) {
        this.config = properties.getTeams();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String channelName() {
        return "teams";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled()
                && config.getWebhookUrl() != null
                && !config.getWebhookUrl().isBlank();
    }

    @Override
    public void send(String title, String message, String severity, Map<String, Object> metadata) {
        if (!isEnabled()) {
            log.debug("Teams channel is disabled; skipping notification");
            return;
        }

        String payload = buildAdaptiveCardPayload(title, message, severity, metadata);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                restTemplate.postForEntity(config.getWebhookUrl(), request, String.class);
                log.info("Teams notification sent: title='{}' attempt={}", title, attempt);
                return;
            } catch (Exception ex) {
                log.warn("Teams delivery failed: attempt={}/{} error={}", attempt, MAX_RETRIES, ex.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleepBackoff(attempt);
                } else {
                    log.error("Teams delivery exhausted all retries for title='{}'", title);
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a Teams Adaptive Card message with title, severity, message body,
     * timestamp and optional metadata facts.
     */
    private String buildAdaptiveCardPayload(String title, String message,
                                            String severity, Map<String, Object> metadata) {
        String severityColor = severityStyle(severity);
        String timestamp = Instant.now().toString();

        String factsJson = buildFactsJson(severity, timestamp, metadata);

        return """
                {
                  "type": "message",
                  "attachments": [
                    {
                      "contentType": "application/vnd.microsoft.card.adaptive",
                      "content": {
                        "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                        "type": "AdaptiveCard",
                        "version": "1.4",
                        "body": [
                          {
                            "type": "TextBlock",
                            "size": "Large",
                            "weight": "Bolder",
                            "text": "%s",
                            "color": "%s"
                          },
                          {
                            "type": "TextBlock",
                            "text": "%s",
                            "wrap": true
                          },
                          {
                            "type": "FactSet",
                            "facts": [
                              %s
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(
                escapeJson(title),
                severityColor,
                escapeJson(message),
                factsJson
        );
    }

    /**
     * Produces JSON array elements for the Adaptive Card FactSet.
     * Always includes Severity and Timestamp; appends any extra metadata keys.
     */
    private String buildFactsJson(String severity, String timestamp, Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append(fact("Severity", severity != null ? severity : "INFO"));
        sb.append(",").append(fact("Timestamp", timestamp));

        if (metadata != null && !metadata.isEmpty()) {
            String extra = metadata.entrySet().stream()
                    .map(e -> fact(e.getKey(), String.valueOf(e.getValue())))
                    .collect(Collectors.joining(","));
            sb.append(",").append(extra);
        }
        return sb.toString();
    }

    private static String fact(String title, String value) {
        return """
                {"title": "%s", "value": "%s"}""".formatted(escapeJson(title), escapeJson(value));
    }

    /**
     * Maps severity to an Adaptive Card {@code TextBlock.color} value.
     */
    private static String severityStyle(String severity) {
        if (severity == null) return "Default";
        return switch (severity.toUpperCase()) {
            case "CRITICAL", "ERROR" -> "Attention";
            case "WARN"              -> "Warning";
            case "INFO"              -> "Good";
            default                  -> "Default";
        };
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
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
