package com.gateway.common.cache;

/**
 * Constants for all named Caffeine cache regions used across the API Gateway Platform.
 */
public final class CacheNames {

    private CacheNames() {
        // Utility class
    }

    public static final String API_KEYS = "apiKeys";
    public static final String PERMISSIONS = "permissions";
    public static final String ROUTE_CONFIG = "routeConfig";
    public static final String JWKS = "jwks";
    public static final String REVOCATION_LIST = "revocationList";
    public static final String API_RESPONSES = "apiResponses";
}
