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

/**
 * Posts alert-style messages to a Slack incoming webhook using Block Kit format.
 * <p>
 * Retry policy: 3 attempts with exponential backoff (1 s, 2 s, 4 s).
 */
@Component
public class SlackNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationChannel.class);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1_000;

    private final NotificationChannelProperties.SlackProperties config;
    private final RestTemplate restTemplate;

    public SlackNotificationChannel(NotificationChannelProperties properties) {
        this.config = properties.getSlack();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String channelName() {
        return "slack";
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
            log.debug("Slack channel is disabled; skipping notification");
            return;
        }

        String color = severityColor(severity);
        String timestamp = Instant.now().toString();

        String payload = buildBlockKitPayload(title, message, color, severity, timestamp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                restTemplate.postForEntity(config.getWebhookUrl(), request, String.class);
                log.info("Slack notification sent: title='{}' attempt={}", title, attempt);
                return;
            } catch (Exception ex) {
                log.warn("Slack delivery failed: attempt={}/{} error={}", attempt, MAX_RETRIES, ex.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleepBackoff(attempt);
                } else {
                    log.error("Slack delivery exhausted all retries for title='{}'", title);
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a Slack Block Kit payload with a colour-coded attachment, title, message,
     * severity badge and an ISO-8601 timestamp.
     */
    private String buildBlockKitPayload(String title, String message, String color,
                                        String severity, String timestamp) {
        // Using manual JSON construction to avoid an extra ObjectMapper dependency in
        // the channel layer.  Values are escaped to prevent injection.
        return """
                {
                  "attachments": [
                    {
                      "color": "%s",
                      "blocks": [
                        {
                          "type": "header",
                          "text": {
                            "type": "plain_text",
                            "text": "%s",
                            "emoji": true
                          }
                        },
                        {
                          "type": "section",
                          "text": {
                            "type": "mrkdwn",
                            "text": "%s"
                          }
                        },
                        {
                          "type": "context",
                          "elements": [
                            {
                              "type": "mrkdwn",
                              "text": "*Severity:* %s  |  *Time:* %s"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                escapeJson(color),
                escapeJson(title),
                escapeJson(message),
                escapeJson(severity),
                escapeJson(timestamp)
        );
    }

    private static String severityColor(String severity) {
        if (severity == null) return "#439FE0"; // info blue
        return switch (severity.toUpperCase()) {
            case "CRITICAL", "ERROR" -> "#E01E5A"; // red
            case "WARN"              -> "#ECB22E"; // amber
            case "INFO"              -> "#439FE0"; // blue
            default                  -> "#439FE0";
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
