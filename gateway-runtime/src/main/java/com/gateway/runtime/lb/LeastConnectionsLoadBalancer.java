package com.gateway.runtime.lb;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least-connections load balancer — selects the upstream URL that currently
 * has the fewest active connections. The proxy handler must call
 * {@link #incrementConnections(String)} before forwarding and
 * {@link #decrementConnections(String)} when the upstream response completes.
 */
@Component
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    @Override
    public String selectUpstream(List<String> upstreamUrls) {
        if (upstreamUrls == null || upstreamUrls.isEmpty()) {
            throw new IllegalArgumentException("No upstream URLs available for load balancing");
        }

        String selected = null;
        int minConnections = Integer.MAX_VALUE;

        for (String url : upstreamUrls) {
            int count = activeConnections
                    .computeIfAbsent(url, k -> new AtomicInteger(0))
                    .get();
            if (count < minConnections) {
                minConnections = count;
                selected = url;
            }
        }

        return selected;
    }

    /**
     * Increment the active connection count for the given upstream URL.
     * Must be called before forwarding the request.
     */
    public void incrementConnections(String url) {
        activeConnections.computeIfAbsent(url, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Decrement the active connection count for the given upstream URL.
     * Must be called when the upstream response completes (in a finally block).
     */
    public void decrementConnections(String url) {
        AtomicInteger count = activeConnections.get(url);
        if (count != null) {
            count.updateAndGet(current -> Math.max(0, current - 1));
        }
    }

    /**
     * Get the current active connection count for the given upstream URL.
     */
    public int getConnectionCount(String url) {
        AtomicInteger count = activeConnections.get(url);
        return count != null ? count.get() : 0;
    }
}
