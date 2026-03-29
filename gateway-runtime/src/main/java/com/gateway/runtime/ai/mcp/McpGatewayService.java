package com.gateway.runtime.ai.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that handles MCP tool invocation through the gateway.
 * Provides RBAC checking, rate limiting, telemetry, and proxying to MCP servers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpGatewayService {

    private final McpToolRegistry toolRegistry;
    private final RestTemplate mcpRestTemplate;

    // ── Rate limiting: per-tool invocation counters (sliding window simplified) ──
    private final Map<String, AtomicLong> rateLimitCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> rateLimitWindowStart = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
    private static final long DEFAULT_MAX_INVOCATIONS_PER_MINUTE = 100;

    // ── Telemetry ───────────────────────────────────────────────────────────────
    private final Map<String, AtomicLong> invocationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalLatencyMs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

    /**
     * Invoke an MCP tool on behalf of a consumer.
     */
    public McpInvocationResponse invoke(McpInvocationRequest request, String consumerId) {
        String toolName = request.getToolName();
        long start = System.currentTimeMillis();

        // a) Find tool in registry
        McpTool tool = toolRegistry.findByName(toolName).orElse(null);
        if (tool == null) {
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("Tool not found: " + toolName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        if (!tool.isEnabled()) {
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("Tool is disabled: " + toolName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // b) Check RBAC: is consumer allowed to use this tool?
        if (!isConsumerAllowed(tool, consumerId)) {
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("Consumer '" + consumerId + "' is not authorized to use tool: " + toolName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // c) Check rate limit per tool
        if (!checkRateLimit(toolName)) {
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("Rate limit exceeded for tool: " + toolName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // d) Call MCP server via REST
        try {
            String url = tool.getServerUrl();
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "invoke";

            Map<String, Object> payload = Map.of(
                    "tool", toolName,
                    "arguments", request.getArguments() != null ? request.getArguments() : Map.of(),
                    "sessionId", request.getSessionId() != null ? request.getSessionId() : ""
            );

            ResponseEntity<Object> response = mcpRestTemplate.postForEntity(url, payload, Object.class);
            long latency = System.currentTimeMillis() - start;

            // e) Track telemetry
            trackTelemetry(toolName, latency, true);

            // f) Return result
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .result(response.getBody())
                    .latencyMs(latency)
                    .success(true)
                    .build();

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            trackTelemetry(toolName, latency, false);

            log.error("MCP invocation failed for tool {}: {}", toolName, ex.getMessage(), ex);
            return McpInvocationResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("Invocation failed: " + ex.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    /**
     * Get invocation statistics for a tool.
     */
    public Map<String, Object> getToolStats(String toolName) {
        long count = invocationCounts.getOrDefault(toolName, new AtomicLong(0)).get();
        long totalLatency = totalLatencyMs.getOrDefault(toolName, new AtomicLong(0)).get();
        long errors = errorCounts.getOrDefault(toolName, new AtomicLong(0)).get();
        double avgLatency = count > 0 ? (double) totalLatency / count : 0.0;

        return Map.of(
                "toolName", toolName,
                "totalInvocations", count,
                "totalErrors", errors,
                "avgLatencyMs", Math.round(avgLatency * 100.0) / 100.0,
                "successRate", count > 0 ? Math.round((count - errors) * 10000.0 / count) / 100.0 : 0.0
        );
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private boolean isConsumerAllowed(McpTool tool, String consumerId) {
        List<String> allowed = tool.getAllowedConsumers();
        // If no allowedConsumers list, the tool is open to all
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        // Wildcard
        if (allowed.contains("*")) {
            return true;
        }
        return allowed.contains(consumerId);
    }

    private boolean checkRateLimit(String toolName) {
        long now = System.currentTimeMillis();
        rateLimitWindowStart.putIfAbsent(toolName, now);
        rateLimitCounters.putIfAbsent(toolName, new AtomicLong(0));

        Long windowStart = rateLimitWindowStart.get(toolName);
        AtomicLong counter = rateLimitCounters.get(toolName);

        // Reset window if expired
        if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
            rateLimitWindowStart.put(toolName, now);
            counter.set(0);
        }

        long current = counter.incrementAndGet();
        return current <= DEFAULT_MAX_INVOCATIONS_PER_MINUTE;
    }

    private void trackTelemetry(String toolName, long latencyMs, boolean success) {
        invocationCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        totalLatencyMs.computeIfAbsent(toolName, k -> new AtomicLong(0)).addAndGet(latencyMs);
        if (!success) {
            errorCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
}
