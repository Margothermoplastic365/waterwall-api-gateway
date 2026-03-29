package com.gateway.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's {@code @Async} support so that {@link com.gateway.notification.channel.NotificationRouter}
 * can dispatch notifications to external channels without blocking the caller thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
