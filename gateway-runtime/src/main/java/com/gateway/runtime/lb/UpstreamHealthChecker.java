package com.gateway.runtime.lb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Periodically pings each upstream URL's /health endpoint to track healthy/unhealthy status.
 * Unhealthy upstreams are removed from rotation; they are added back when health checks pass.
 */
@Slf4j
@Component
public class UpstreamHealthChecker {

    private final RestClient restClient;

    /**
     * Tracks the health status of each upstream URL.
     */
    private final ConcurrentHashMap<String, UpstreamStatus> statusMap = new ConcurrentHashMap<>();

    public UpstreamHealthChecker(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Register upstream URLs for health checking.
     */
    public void registerUpstream(String upstreamUrl) {
        statusMap.putIfAbsent(upstreamUrl, new UpstreamStatus());
    }

    /**
     * Check if an upstream URL is healthy.
     */
    public boolean isHealthy(String upstreamUrl) {
        UpstreamStatus status = statusMap.get(upstreamUrl);
        return status == null || status.isHealthy();
    }

    /**
     * Filter a list of upstream URLs to only include healthy ones.
     */
    public List<String> filterHealthy(List<String> upstreamUrls) {
        List<String> healthy = upstreamUrls.stream()
                .filter(this::isHealthy)
                .collect(Collectors.toList());
        // If all are unhealthy, return the original list as fallback
        return healthy.isEmpty() ? upstreamUrls : healthy;
    }

    /**
     * Periodically ping each registered upstream's /health endpoint.
     */
    @Scheduled(fixedRateString = "${gateway.runtime.lb.health-check-interval-ms:30000}")
    public void checkHealth() {
        if (statusMap.isEmpty()) {
            return;
        }

        log.debug("Running upstream health checks for {} endpoints", statusMap.size());

        for (Map.Entry<String, UpstreamStatus> entry : statusMap.entrySet()) {
            String url = entry.getKey();
            UpstreamStatus status = entry.getValue();
            checkSingleUpstream(url, status);
        }
    }

    private void checkSingleUpstream(String upstreamUrl, UpstreamStatus status) {
        String healthUrl = upstreamUrl.endsWith("/")
                ? upstreamUrl + "health"
                : upstreamUrl + "/health";

        try {
            restClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity();

            if (!status.isHealthy()) {
                log.info("Upstream recovered: {}", upstreamUrl);
            }
            status.recordSuccess();
        } catch (Exception e) {
            status.recordFailure();
            log.warn("Upstream health check failed for {}: {} (consecutive failures: {})",
                    upstreamUrl, e.getMessage(), status.getConsecutiveFailures());
        }
    }

    /**
     * Get all registered upstream statuses (for monitoring).
     */
    public Map<String, UpstreamStatus> getAllStatuses() {
        return Map.copyOf(statusMap);
    }

    /**
     * Tracks the health status of a single upstream.
     */
    public static class UpstreamStatus {
        private volatile boolean healthy = true;
        private volatile int consecutiveFailures = 0;

        public boolean isHealthy() {
            return healthy;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public synchronized void recordSuccess() {
            this.healthy = true;
            this.consecutiveFailures = 0;
        }

        public synchronized void recordFailure() {
            this.consecutiveFailures++;
            if (this.consecutiveFailures >= 3) {
                this.healthy = false;
            }
        }
    }
}
