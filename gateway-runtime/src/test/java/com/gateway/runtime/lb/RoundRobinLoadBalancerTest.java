package com.gateway.runtime.lb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinLoadBalancerTest {

    private RoundRobinLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new RoundRobinLoadBalancer();
    }

    @Test
    void shouldSelectFirstUpstream() {
        List<String> urls = List.of("http://host1:8080", "http://host2:8080", "http://host3:8080");

        String selected = loadBalancer.selectUpstream(urls);

        assertThat(selected).isEqualTo("http://host1:8080");
    }

    @Test
    void shouldCycleThroughUpstreams() {
        List<String> urls = List.of("http://host1:8080", "http://host2:8080", "http://host3:8080");

        String first = loadBalancer.selectUpstream(urls);
        String second = loadBalancer.selectUpstream(urls);
        String third = loadBalancer.selectUpstream(urls);
        String fourth = loadBalancer.selectUpstream(urls);

        assertThat(first).isEqualTo("http://host1:8080");
        assertThat(second).isEqualTo("http://host2:8080");
        assertThat(third).isEqualTo("http://host3:8080");
        // Fourth call wraps around to the first URL
        assertThat(fourth).isEqualTo("http://host1:8080");
    }

    @Test
    void shouldHandleSingleUpstream() {
        List<String> urls = List.of("http://only-host:8080");

        String first = loadBalancer.selectUpstream(urls);
        String second = loadBalancer.selectUpstream(urls);
        String third = loadBalancer.selectUpstream(urls);

        assertThat(first).isEqualTo("http://only-host:8080");
        assertThat(second).isEqualTo("http://only-host:8080");
        assertThat(third).isEqualTo("http://only-host:8080");
    }

    @Test
    void shouldThrowOnEmptyList() {
        assertThatThrownBy(() -> loadBalancer.selectUpstream(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No upstream URLs");
    }

    @Test
    void shouldThrowOnNullList() {
        assertThatThrownBy(() -> loadBalancer.selectUpstream(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No upstream URLs");
    }
}
