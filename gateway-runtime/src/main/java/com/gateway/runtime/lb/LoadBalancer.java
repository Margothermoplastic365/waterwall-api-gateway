package com.gateway.runtime.lb;

import java.util.List;

/**
 * Strategy interface for selecting an upstream URL from a list of candidates.
 */
public interface LoadBalancer {

    /**
     * Select the next upstream URL to route a request to.
     *
     * @param upstreamUrls the list of available upstream URLs
     * @return the selected upstream URL
     * @throws IllegalArgumentException if the list is null or empty
     */
    String selectUpstream(List<String> upstreamUrls);
}
