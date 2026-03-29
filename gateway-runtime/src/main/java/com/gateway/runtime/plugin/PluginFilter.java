package com.gateway.runtime.plugin;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Order(44) — Executes all registered gateway plugins in order.
 * Calls preRequest before forwarding, and postResponse after.
 */
@Slf4j
@Component
@Order(44)
@RequiredArgsConstructor
public class PluginFilter implements Filter {

    private final PluginRegistry pluginRegistry;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        List<GatewayPlugin> plugins = pluginRegistry.getPlugins();

        // Execute preRequest for all plugins
        for (GatewayPlugin plugin : plugins) {
            try {
                plugin.preRequest(request, response);
            } catch (Exception e) {
                log.error("Plugin '{}' preRequest failed: {}", plugin.getName(), e.getMessage());
                handlePluginError(plugins, request, response, e);
                return;
            }
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            handlePluginError(plugins, request, response, e);
            throw e;
        }

        // Execute postResponse for all plugins (reverse order)
        for (int i = plugins.size() - 1; i >= 0; i--) {
            GatewayPlugin plugin = plugins.get(i);
            try {
                plugin.postResponse(request, response);
            } catch (Exception e) {
                log.error("Plugin '{}' postResponse failed: {}", plugin.getName(), e.getMessage());
            }
        }
    }

    private void handlePluginError(List<GatewayPlugin> plugins,
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    Exception exception) {
        for (GatewayPlugin plugin : plugins) {
            try {
                plugin.onError(request, response, exception);
            } catch (Exception e) {
                log.error("Plugin '{}' onError handler failed: {}", plugin.getName(), e.getMessage());
            }
        }
    }
}
