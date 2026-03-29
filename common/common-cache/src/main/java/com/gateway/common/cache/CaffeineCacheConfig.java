package com.gateway.common.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Configures named Caffeine caches with per-cache TTL and size settings
 * as defined in the Two-Tier Cache architecture (Section 2.5.5).
 *
 * <p>Tier 1 caches use RabbitMQ fanout for cross-node invalidation.
 * Tier 2 ({@code revocationList}) relies on a short 5-second TTL for
 * near-real-time consistency without MQ dependency.</p>
 *
 * <p>Rate-limit counters use a plain {@link ConcurrentHashMap} (via
 * {@link RateLimitCounter}) because they require atomic increment
 * semantics that Caffeine's Cache interface does not provide.</p>
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfig {

    /**
     * Per-cache Caffeine specifications keyed by cache name.
     */
    private static final Map<String, Caffeine<Object, Object>> CACHE_SPECS = Map.of(
            CacheNames.API_KEYS,
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats(),

            CacheNames.PERMISSIONS,
            Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(2, TimeUnit.MINUTES)
                    .recordStats(),

            CacheNames.ROUTE_CONFIG,
            Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .recordStats(),
            // No TTL — manual invalidation only

            CacheNames.JWKS,
            Caffeine.newBuilder()
                    .maximumSize(10)
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .recordStats(),

            CacheNames.REVOCATION_LIST,
            Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(5, TimeUnit.SECONDS)
                    .recordStats(),
            // Tier 2: security-critical, short TTL refresh from PostgreSQL

            CacheNames.API_RESPONSES,
            Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats()
    );

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
                Caffeine<Object, Object> spec = CACHE_SPECS.get(name);
                if (spec != null) {
                    return spec.build();
                }
                // Fallback for any undeclared cache: reasonable defaults
                return Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build();
            }
        };
        // Register all known cache names so they are created eagerly
        manager.setCacheNames(CACHE_SPECS.keySet());
        return manager;
    }

    /**
     * Simple ConcurrentHashMap-based store for rate-limit counters.
     * NOT a Caffeine cache — requires atomic {@code incrementAndGet} semantics.
     */
    @Bean
    public RateLimitCounter rateLimitCounter() {
        return new RateLimitCounter();
    }
}
