package com.gateway.management.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Per-API gateway configuration — controls load balancing, circuit breaker,
 * caching, rate limiting, IP filtering, security headers, and timeouts.
 * All fields are optional. Null fields inherit the global gateway defaults.
 */
@Data
public class ApiGatewayConfigRequest {

    private LoadBalancingConfig loadBalancing;
    private CircuitBreakerConfig circuitBreaker;
    private CachingConfig caching;
    private RateLimitOverrideConfig rateLimitOverride;
    private IpFilterConfig ipFilter;
    private SecurityConfig security;
    private TimeoutConfig timeouts;

    @Data
    public static class LoadBalancingConfig {
        private List<String> upstreamUrls;
        private String algorithm = "round-robin";       // round-robin, weighted
        private HealthCheckConfig healthCheck;
    }

    @Data
    public static class HealthCheckConfig {
        private boolean enabled = true;
        private String path = "/health";
        private int intervalSeconds = 30;
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private int failureThreshold = 5;
        private int resetTimeoutSeconds = 60;
        private int halfOpenMaxRequests = 1;
    }

    @Data
    public static class CachingConfig {
        private boolean enabled = false;
        private int ttlSeconds = 300;
        private List<String> cacheableMethods;          // default: ["GET"]
        private List<Integer> cacheableStatuses;        // default: [200]
        private boolean includeQueryParams = true;
        private boolean includeAuthHeader = false;
    }

    @Data
    public static class RateLimitOverrideConfig {
        private boolean enabled = false;
        private Integer requestsPerSecond;
        private Integer requestsPerMinute;
        private Integer requestsPerDay;
        private Integer burstAllowance;
        private String enforcement = "SOFT";            // SOFT or STRICT
    }

    @Data
    public static class IpFilterConfig {
        private List<String> whitelist;                 // CIDR notation supported
        private List<String> blacklist;
    }

    @Data
    public static class SecurityConfig {
        private List<String> corsOrigins;
        private Map<String, String> customHeaders;
    }

    @Data
    public static class TimeoutConfig {
        private Integer connectTimeoutMs = 10000;
        private Integer readTimeoutMs = 60000;
        private Integer maxRequestBodyBytes = 10485760; // 10MB
    }
}
