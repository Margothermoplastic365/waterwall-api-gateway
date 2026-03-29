package com.gateway.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central dispatcher that fans-out a notification to every enabled
 * {@link NotificationChannel}.
 * <p>
 * Channel implementations are auto-discovered via Spring's component scan.
 * Each call to {@link #route} iterates over every enabled channel and
 * delivers the notification asynchronously (the method is annotated
 * with {@code @Async}).
 * <p>
 * If a single channel fails the remaining channels are still attempted.
 */
@Component
public class NotificationRouter {

    private static final Logger log = LoggerFactory.getLogger(NotificationRouter.class);

    private final List<NotificationChannel> channels;

    /**
     * Spring injects every bean that implements {@link NotificationChannel}.
     */
    public NotificationRouter(List<NotificationChannel> channels) {
        this.channels = channels;
        List<String> enabled = channels.stream()
                .filter(NotificationChannel::isEnabled)
                .map(NotificationChannel::channelName)
                .collect(Collectors.toList());
        log.info("NotificationRouter initialised with enabled channels: {}", enabled);
    }

    /**
     * Fan-out a notification to <b>all</b> enabled channels.
     * Runs asynchronously so the caller is not blocked by slow HTTP calls.
     *
     * @param title     human-readable title
     * @param message   notification body
     * @param severity  INFO / WARN / ERROR / CRITICAL
     * @param metadata  extra key/value pairs rendered as facts / fields
     */
    @Async
    public void route(String title, String message, String severity, Map<String, Object> metadata) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                continue;
            }
            try {
                log.debug("Routing notification to channel={} title='{}'", channel.channelName(), title);
                channel.send(title, message, severity, metadata);
            } catch (Exception ex) {
                log.error("Unhandled error routing to channel={}: {}",
                        channel.channelName(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Fan-out a notification to a specific subset of channels identified by name.
     * Channels that are disabled or not in the requested list are skipped.
     *
     * @param channelNames names such as {@code "slack"}, {@code "teams"}, {@code "webhook"}
     */
    @Async
    public void routeTo(List<String> channelNames, String title, String message,
                        String severity, Map<String, Object> metadata) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                continue;
            }
            if (!channelNames.contains(channel.channelName())) {
                continue;
            }
            try {
                log.debug("Routing notification to channel={} title='{}'", channel.channelName(), title);
                channel.send(title, message, severity, metadata);
            } catch (Exception ex) {
                log.error("Unhandled error routing to channel={}: {}",
                        channel.channelName(), ex.getMessage(), ex);
            }
        }
    }
}
