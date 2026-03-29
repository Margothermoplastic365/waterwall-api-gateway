package com.gateway.runtime.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for gateway plugins. Manages plugin registration, unregistration,
 * and provides ordered access to all registered plugins.
 */
@Slf4j
@Component
public class PluginRegistry {

    private final CopyOnWriteArrayList<GatewayPlugin> plugins = new CopyOnWriteArrayList<>();

    /**
     * Register a plugin. Plugins are ordered by their getOrder() value.
     */
    public void register(GatewayPlugin plugin) {
        plugins.add(plugin);
        log.info("Registered gateway plugin: {} (order={})", plugin.getName(), plugin.getOrder());
    }

    /**
     * Unregister a plugin by name.
     */
    public void unregister(String pluginName) {
        boolean removed = plugins.removeIf(p -> p.getName().equals(pluginName));
        if (removed) {
            log.info("Unregistered gateway plugin: {}", pluginName);
        }
    }

    /**
     * Get all registered plugins, sorted by execution order.
     */
    public List<GatewayPlugin> getPlugins() {
        return plugins.stream()
                .sorted(Comparator.comparingInt(GatewayPlugin::getOrder))
                .toList();
    }

    /**
     * Get the number of registered plugins.
     */
    public int size() {
        return plugins.size();
    }
}
