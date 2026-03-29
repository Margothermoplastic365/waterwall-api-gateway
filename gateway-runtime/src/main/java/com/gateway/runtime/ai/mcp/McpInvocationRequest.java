package com.gateway.runtime.ai.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request payload for invoking an MCP tool through the gateway.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpInvocationRequest {

    private String toolName;
    private Map<String, Object> arguments;
    private String sessionId;
}
