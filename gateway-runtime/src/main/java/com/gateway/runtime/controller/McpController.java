package com.gateway.runtime.controller;

import com.gateway.runtime.ai.mcp.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the MCP Tool Gateway.
 * Exposes endpoints to register, unregister, list, invoke, and monitor MCP tools.
 */
@RestController
@RequestMapping("/v1/ai/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpToolRegistry toolRegistry;
    private final McpGatewayService mcpGatewayService;

    /**
     * List all available MCP tools.
     */
    @GetMapping("/tools")
    public ResponseEntity<List<McpTool>> listTools() {
        return ResponseEntity.ok(toolRegistry.listAll());
    }

    /**
     * Register a new MCP tool (admin operation).
     */
    @PostMapping("/tools")
    public ResponseEntity<McpTool> registerTool(@RequestBody McpTool tool) {
        McpTool registered = toolRegistry.register(tool);
        return ResponseEntity.ok(registered);
    }

    /**
     * Unregister an MCP tool by name.
     */
    @DeleteMapping("/tools/{name}")
    public ResponseEntity<Void> unregisterTool(@PathVariable String name) {
        boolean removed = toolRegistry.unregister(name);
        if (removed) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Invoke an MCP tool.
     */
    @PostMapping("/invoke")
    public ResponseEntity<McpInvocationResponse> invoke(
            @RequestBody McpInvocationRequest request,
            @RequestHeader(value = "X-Consumer-Id", defaultValue = "anonymous") String consumerId) {
        McpInvocationResponse response = mcpGatewayService.invoke(request, consumerId);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Get invocation stats for a specific tool.
     */
    @GetMapping("/tools/{name}/stats")
    public ResponseEntity<Map<String, Object>> getToolStats(@PathVariable String name) {
        if (!toolRegistry.isRegistered(name)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mcpGatewayService.getToolStats(name));
    }
}
