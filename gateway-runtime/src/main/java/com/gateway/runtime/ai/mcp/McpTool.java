package com.gateway.runtime.ai.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents an MCP (Model Context Protocol) tool registered in the gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpTool {

    private String name;
    private String description;
    private String serverUrl;
    private Map<String, Object> inputSchema;
    private List<String> allowedConsumers;
    private boolean enabled;
    private Instant registeredAt;
}
