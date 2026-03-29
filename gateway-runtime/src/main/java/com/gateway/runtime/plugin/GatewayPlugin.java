package com.gateway.runtime.plugin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Plugin SDK interface for the API Gateway.
 * Implement this interface to create custom plugins that hook into
 * the gateway request/response lifecycle.
 */
public interface GatewayPlugin {

    /**
     * @return the unique name of this plugin
     */
    String getName();

    /**
     * @return the execution order (lower values execute first)
     */
    int getOrder();

    /**
     * Called before the request is forwarded to the upstream service.
     */
    void preRequest(HttpServletRequest request, HttpServletResponse response);

    /**
     * Called after the upstream response is received, before sending to the client.
     */
    void postResponse(HttpServletRequest request, HttpServletResponse response);

    /**
     * Called when an error occurs during request processing.
     */
    void onError(HttpServletRequest request, HttpServletResponse response, Exception exception);
}
