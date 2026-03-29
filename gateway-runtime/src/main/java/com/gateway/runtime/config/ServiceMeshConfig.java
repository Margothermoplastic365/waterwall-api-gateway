package com.gateway.runtime.config;

import com.gateway.runtime.service.ServiceDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Service Mesh integration configuration.
 * Enables the gateway to act as a K8s ingress controller and resolve
 * upstream service URLs via Kubernetes DNS, Docker Compose service names,
 * or static IP configuration.
 *
 * Activated when gateway.service-mesh.enabled=true.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "gateway.service-mesh.enabled", havingValue = "true")
public class ServiceMeshConfig {

    @Bean
    @ConfigurationProperties(prefix = "gateway.service-mesh")
    public ServiceMeshProperties serviceMeshProperties() {
        return new ServiceMeshProperties();
    }

    @Bean
    public ServiceDiscoveryService serviceDiscoveryService(ServiceMeshProperties properties) {
        log.info("Service mesh integration enabled — discovery-type={}, k8s-namespace={}",
                properties.getDiscoveryType(), properties.getK8sNamespace());
        return new ServiceDiscoveryService(properties);
    }

    @Data
    public static class ServiceMeshProperties {

        /**
         * Discovery type: static, docker, or kubernetes.
         */
        private String discoveryType = "static";

        /**
         * Kubernetes namespace for service resolution.
         */
        private String k8sNamespace = "default";

        /**
         * Default port to use when resolving services that don't specify one.
         */
        private int defaultPort = 8080;

        /**
         * Cluster domain suffix for Kubernetes DNS resolution.
         */
        private String clusterDomain = "cluster.local";
    }
}
