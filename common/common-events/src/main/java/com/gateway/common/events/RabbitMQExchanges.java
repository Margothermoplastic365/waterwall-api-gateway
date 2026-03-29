package com.gateway.common.events;

/**
 * Constants for all RabbitMQ exchange names used across the API Gateway Platform.
 * Exchange types are declared in {@link RabbitMQConfig}.
 */
public final class RabbitMQExchanges {

    private RabbitMQExchanges() {
        // Utility class — no instantiation
    }

    /** Topic exchange for all platform domain events (api.published, user.registered, etc.) */
    public static final String PLATFORM_EVENTS = "platform.events";

    /** Fanout exchange for cache eviction broadcasts across all nodes */
    public static final String CACHE_INVALIDATE = "cache.invalidate";

    /** Fanout exchange for rate-limit counter synchronisation across gateway nodes */
    public static final String RATELIMIT_SYNC = "ratelimit.sync";

    /** Topic exchange for notification dispatch (email, webhook, in-app) */
    public static final String NOTIFICATIONS = "notifications";

    /** Topic exchange for analytics event ingestion (request.logged, metric.emitted) */
    public static final String ANALYTICS_INGEST = "analytics.ingest";

    /** Topic exchange for audit event streaming to analytics-service */
    public static final String AUDIT_EVENTS = "audit.events";

    /** Fanout exchange for account-lockout state synchronisation across nodes */
    public static final String LOCKOUT_SYNC = "lockout.sync";

    /** Fanout exchange for configuration refresh broadcasts */
    public static final String CONFIG_REFRESH = "config.refresh";
}
