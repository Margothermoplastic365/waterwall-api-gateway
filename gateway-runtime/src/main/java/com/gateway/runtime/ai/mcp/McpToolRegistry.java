package com.gateway.runtime.ai.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for MCP tools.
 * Supports registration, unregistration, and lookup of tools.
 */
@Slf4j
@Service
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    /**
     * Register (or update) an MCP tool in the registry.
     */
    public McpTool register(McpTool tool) {
        if (tool.getRegisteredAt() == null) {
            tool.setRegisteredAt(Instant.now());
        }
        tools.put(tool.getName(), tool);
        log.info("Registered MCP tool: name={} serverUrl={}", tool.getName(), tool.getServerUrl());
        return tool;
    }

    /**
     * Unregister an MCP tool by name.
     *
     * @return true if the tool was found and removed
     */
    public boolean unregister(String name) {
        McpTool removed = tools.remove(name);
        if (removed != null) {
            log.info("Unregistered MCP tool: {}", name);
            return true;
        }
        log.warn("Attempted to unregister unknown MCP tool: {}", name);
        return false;
    }

    /**
     * Find a tool by name.
     */
    public Optional<McpTool> findByName(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * List all registered tools.
     */
    public List<McpTool> listAll() {
        return new ArrayList<>(tools.values());
    }

    /**
     * List all enabled tools.
     */
    public List<McpTool> listEnabled() {
        return tools.values().stream()
                .filter(McpTool::isEnabled)
                .toList();
    }

    /**
     * Check if a tool is registered.
     */
    public boolean isRegistered(String name) {
        return tools.containsKey(name);
    }
}
