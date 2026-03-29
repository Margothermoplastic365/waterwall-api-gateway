package com.gateway.runtime.ai.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from an MCP tool invocation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpInvocationResponse {

    private String toolName;
    private Object result;
    private long latencyMs;
    private boolean success;
    private String errorMessage;
}
