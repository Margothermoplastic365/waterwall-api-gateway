package com.gateway.runtime.lb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeastConnectionsLoadBalancerTest {

    private LeastConnectionsLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new LeastConnectionsLoadBalancer();
    }

    @Test
    void shouldSelectUpstreamWithLeastConnections() {
        String url1 = "http://host1:8080";
        String url2 = "http://host2:8080";
        List<String> urls = List.of(url1, url2);

        // Give url1 two active connections, url2 has none
        loadBalancer.incrementConnections(url1);
        loadBalancer.incrementConnections(url1);

        String selected = loadBalancer.selectUpstream(urls);

        assertThat(selected).isEqualTo(url2);
    }

    @Test
    void shouldSelectFirstWhenAllEqual() {
        String url1 = "http://host1:8080";
        String url2 = "http://host2:8080";
        String url3 = "http://host3:8080";
        List<String> urls = List.of(url1, url2, url3);

        // No connections on any URL — all equal, first should be selected
        String selected = loadBalancer.selectUpstream(urls);

        assertThat(selected).isEqualTo(url1);
    }

    @Test
    void shouldIncrementAndDecrementConnections() {
        String url = "http://host1:8080";

        assertThat(loadBalancer.getConnectionCount(url)).isZero();

        loadBalancer.incrementConnections(url);
        assertThat(loadBalancer.getConnectionCount(url)).isEqualTo(1);

        loadBalancer.incrementConnections(url);
        assertThat(loadBalancer.getConnectionCount(url)).isEqualTo(2);

        loadBalancer.decrementConnections(url);
        assertThat(loadBalancer.getConnectionCount(url)).isEqualTo(1);

        loadBalancer.decrementConnections(url);
        assertThat(loadBalancer.getConnectionCount(url)).isZero();
    }

    @Test
    void shouldNotGoBelowZero() {
        String url = "http://host1:8080";

        // Increment once then decrement twice
        loadBalancer.incrementConnections(url);
        loadBalancer.decrementConnections(url);
        loadBalancer.decrementConnections(url);

        assertThat(loadBalancer.getConnectionCount(url)).isZero();
    }

    @Test
    void shouldReturnZeroForUnknownUrl() {
        assertThat(loadBalancer.getConnectionCount("http://unknown:8080")).isZero();
    }

    @Test
    void shouldThrowOnEmptyList() {
        assertThatThrownBy(() -> loadBalancer.selectUpstream(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
