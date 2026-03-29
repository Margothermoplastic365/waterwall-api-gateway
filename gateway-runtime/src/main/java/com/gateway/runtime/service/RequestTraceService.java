package com.gateway.runtime.service;

import com.gateway.runtime.dto.TraceEntry;
import com.gateway.runtime.dto.TraceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures request traces for debugging purposes.
 * Stores traces in memory with a 5-minute TTL.
 */
@Slf4j
@Service
public class RequestTraceService {

    private final Map<String, TraceResult> traceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> traceTimestamps = new ConcurrentHashMap<>();
    private static final long TTL_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Simulate tracing a request through the filter pipeline.
     */
    public TraceResult traceRequest(String path, String method, Map<String, String> headers, String body) {
        String traceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        List<TraceEntry> entries = new ArrayList<>();

        // Simulate filter pipeline stages
        entries.add(simulateFilter("RouteMatchFilter", 10, path));
        entries.add(simulateFilter("SecurityHeadersFilter", 15, null));
        entries.add(simulateFilter("IpFilterFilter", 20, null));
        entries.add(simulateFilter("GatewayAuthFilter", 30, null));
        entries.add(simulateFilter("SubscriptionCheckFilter", 40, null));
        entries.add(simulateFilter("TransformationFilter", 43, null));
        entries.add(simulateFilter("PluginFilter", 44, null));
        entries.add(simulateFilter("ChaosFilter", 48, null));
        entries.add(simulateFilter("RateLimitFilter", 50, null));
        entries.add(simulateFilter("ResponseCacheFilter", 55, null));
        entries.add(simulateFilter("AccessLogFilter", 100, null));

        long totalDuration = System.currentTimeMillis() - startTime;

        TraceResult result = TraceResult.builder()
                .traceId(traceId)
                .entries(entries)
                .finalStatus(200)
                .totalDurationMs(totalDuration)
                .build();

        // Store in cache with TTL
        traceCache.put(traceId, result);
        traceTimestamps.put(traceId, System.currentTimeMillis());
        evictExpired();

        log.debug("Traced request: traceId={}, path={}, method={}", traceId, path, method);
        return result;
    }

    public TraceResult getTrace(String traceId) {
        evictExpired();
        return traceCache.get(traceId);
    }

    private TraceEntry simulateFilter(String name, int order, String detail) {
        long start = System.nanoTime();
        // Simulate minimal processing
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        Map<String, Object> details = new LinkedHashMap<>();
        if (detail != null) {
            details.put("info", detail);
        }

        return TraceEntry.builder()
                .filterName(name)
                .order(order)
                .decision("PASS")
                .durationMs(durationMs)
                .details(details)
                .build();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        traceTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > TTL_MS) {
                traceCache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
