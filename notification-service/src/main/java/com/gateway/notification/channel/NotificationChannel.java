package com.gateway.notification.channel;

import java.util.Map;

/**
 * Common contract for every outbound notification channel (Slack, Teams, generic webhook, etc.).
 * Implementations are expected to handle their own retry logic.
 */
public interface NotificationChannel {

    /**
     * @return a short identifier such as {@code "slack"}, {@code "teams"}, or {@code "webhook"}.
     */
    String channelName();

    /**
     * @return {@code true} when the channel has been enabled in configuration and has the
     *         minimum required settings (e.g. a webhook URL).
     */
    boolean isEnabled();

    /**
     * Deliver a notification through this channel.
     *
     * @param title     human-readable title / subject
     * @param message   the body of the notification
     * @param severity  one of INFO, WARN, ERROR, CRITICAL (used for colouring)
     * @param metadata  arbitrary key/value pairs that a channel can render as "facts" or fields
     */
    void send(String title, String message, String severity, Map<String, Object> metadata);
}
