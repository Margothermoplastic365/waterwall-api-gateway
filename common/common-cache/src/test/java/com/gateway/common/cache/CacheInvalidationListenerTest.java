package com.gateway.common.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private CacheInvalidationListener listener;

    @Test
    void shouldEvictSpecificKey() {
        String cacheName = "apiKeys";
        String cacheKey = "key-42";

        when(cacheManager.getCache(cacheName)).thenReturn(cache);

        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .cacheName(cacheName)
                .cacheKey(cacheKey)
                .reason("api key rotated")
                .sourceNodeId("node-1")
                .build();

        listener.onCacheInvalidation(event);

        verify(cache).evict(cacheKey);
    }

    @Test
    void shouldClearEntireCacheWhenKeyIsNull() {
        String cacheName = "permissions";

        when(cacheManager.getCache(cacheName)).thenReturn(cache);

        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .cacheName(cacheName)
                .cacheKey(null)
                .reason("bulk update")
                .sourceNodeId("node-2")
                .build();

        listener.onCacheInvalidation(event);

        verify(cache).clear();
    }

    @Test
    void shouldIgnoreUnknownCacheName() {
        String cacheName = "nonExistentCache";

        when(cacheManager.getCache(cacheName)).thenReturn(null);

        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .cacheName(cacheName)
                .cacheKey("some-key")
                .reason("test")
                .sourceNodeId("node-3")
                .build();

        assertThatCode(() -> listener.onCacheInvalidation(event))
                .doesNotThrowAnyException();

        verifyNoInteractions(cache);
    }
}
