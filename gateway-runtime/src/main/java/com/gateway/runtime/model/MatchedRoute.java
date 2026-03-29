package com.gateway.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of route matching — contains the matched route and any
 * path variables extracted from the request path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchedRoute {

    private GatewayRoute route;
    private Map<String, String> pathVariables;
}
