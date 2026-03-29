package com.gateway.runtime.lb;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer that rotates across upstream URLs.
 * Marked as {@link Primary} so it is the default when injecting {@link LoadBalancer}.
 */
@Primary
@Component
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String selectUpstream(List<String> upstreamUrls) {
        if (upstreamUrls == null || upstreamUrls.isEmpty()) {
            throw new IllegalArgumentException("No upstream URLs available for load balancing");
        }
        int index = Math.abs(counter.getAndIncrement() % upstreamUrls.size());
        return upstreamUrls.get(index);
    }
}
