package com.gateway.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code gateway.notifications.channels} section of application.yml
 * into a strongly-typed configuration object.
 */
@Configuration
@ConfigurationProperties(prefix = "gateway.notifications.channels")
public class NotificationChannelProperties {

    private SlackProperties slack = new SlackProperties();
    private TeamsProperties teams = new TeamsProperties();
    private WebhookProperties webhook = new WebhookProperties();

    // ── Slack ────────────────────────────────────────────────────────────

    public static class SlackProperties {
        private boolean enabled = false;
        private String webhookUrl = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    // ── Teams ────────────────────────────────────────────────────────────

    public static class TeamsProperties {
        private boolean enabled = false;
        private String webhookUrl = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    // ── Generic Webhook ──────────────────────────────────────────────────

    public static class WebhookProperties {
        private boolean enabled = false;
        private String url = "";
        private String secret = "";
        private Map<String, String> headers = new HashMap<>();
        private int maxRetries = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    // ── Top-level accessors ──────────────────────────────────────────────

    public SlackProperties getSlack() { return slack; }
    public void setSlack(SlackProperties slack) { this.slack = slack; }
    public TeamsProperties getTeams() { return teams; }
    public void setTeams(TeamsProperties teams) { this.teams = teams; }
    public WebhookProperties getWebhook() { return webhook; }
    public void setWebhook(WebhookProperties webhook) { this.webhook = webhook; }
}
