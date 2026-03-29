package com.gateway.runtime.controller;

import com.gateway.common.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoint for cache management operations.
 * Allows purging cached responses for specific APIs or all cached responses.
 */
@Slf4j
@RestController
@RequestMapping("/v1/gateway/cache")
@RequiredArgsConstructor
public class CacheManagementController {

    private final CacheManager cacheManager;

    /**
     * Purge cached responses for a specific API.
     * Note: Caffeine does not support prefix-based invalidation, so this clears the entire cache.
     * For production, a more granular approach with a secondary index would be needed.
     */
    @DeleteMapping("/purge")
    public ResponseEntity<String> purgeApiCache(@RequestParam("apiId") UUID apiId) {
        Cache cache = cacheManager.getCache(CacheNames.API_RESPONSES);
        if (cache != null) {
            cache.clear();
            log.info("Purged response cache for apiId={}", apiId);
        }
        return ResponseEntity.ok("{\"message\":\"Cache purged for API " + apiId + "\"}");
    }

    /**
     * Purge all cached responses.
     */
    @DeleteMapping("/purge-all")
    public ResponseEntity<String> purgeAllCache() {
        Cache cache = cacheManager.getCache(CacheNames.API_RESPONSES);
        if (cache != null) {
            cache.clear();
            log.info("Purged all response caches");
        }
        return ResponseEntity.ok("{\"message\":\"All response caches purged\"}");
    }
}
