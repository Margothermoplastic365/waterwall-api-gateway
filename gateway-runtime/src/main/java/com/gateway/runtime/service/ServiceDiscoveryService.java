package com.gateway.runtime.service;

import com.gateway.runtime.config.ServiceMeshConfig.ServiceMeshProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves upstream service addresses based on the configured discovery type.
 *
 * Supports:
 *   - static:     Uses statically configured service URLs from application.yml
 *   - docker:     Resolves Docker Compose service names via DNS
 *   - kubernetes: Resolves K8s service names using service.namespace.svc.clusterDomain
 *
 * This bean is created by {@link com.gateway.runtime.config.ServiceMeshConfig}
 * only when gateway.service-mesh.enabled=true.
 */
@Slf4j
public class ServiceDiscoveryService {

    private final ServiceMeshProperties properties;

    /**
     * Static service registry: populated from configuration or via API.
     * Maps service-name to host:port.
     */
    private final Map<String, String> staticRegistry = new ConcurrentHashMap<>();

    public ServiceDiscoveryService(ServiceMeshProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve a service name to a routable upstream URL (host:port).
     *
     * @param serviceName the logical service name (e.g., "user-service", "order-service")
     * @return the resolved address as host:port
     */
    public String resolveUpstream(String serviceName) {
        return switch (properties.getDiscoveryType().toLowerCase()) {
            case "kubernetes", "k8s" -> resolveKubernetes(serviceName);
            case "docker" -> resolveDocker(serviceName);
            default -> resolveStatic(serviceName);
        };
    }

    /**
     * Register a static service mapping.
     */
    public void registerStatic(String serviceName, String hostPort) {
        staticRegistry.put(serviceName, hostPort);
        log.info("Registered static service: {} -> {}", serviceName, hostPort);
    }

    /**
     * Remove a static service mapping.
     */
    public void deregisterStatic(String serviceName) {
        staticRegistry.remove(serviceName);
        log.info("Deregistered static service: {}", serviceName);
    }

    /**
     * Get all registered static services.
     */
    public Map<String, String> listStaticServices() {
        return Map.copyOf(staticRegistry);
    }

    // ── Discovery strategies ─────────────────────────────────────────────

    /**
     * Kubernetes DNS resolution.
     * K8s services are reachable at: service-name.namespace.svc.cluster-domain
     */
    private String resolveKubernetes(String serviceName) {
        String fqdn = String.format("%s.%s.svc.%s",
                serviceName,
                properties.getK8sNamespace(),
                properties.getClusterDomain());

        try {
            InetAddress address = InetAddress.getByName(fqdn);
            String resolved = address.getHostAddress() + ":" + properties.getDefaultPort();
            log.debug("K8s resolved {} -> {} ({})", serviceName, fqdn, resolved);
            return resolved;
        } catch (Exception e) {
            log.warn("K8s DNS resolution failed for {}: {}. Falling back to FQDN.", serviceName, e.getMessage());
            return fqdn + ":" + properties.getDefaultPort();
        }
    }

    /**
     * Docker Compose service resolution.
     * Docker Compose services are reachable by their service name via the internal Docker DNS.
     */
    private String resolveDocker(String serviceName) {
        try {
            InetAddress address = InetAddress.getByName(serviceName);
            String resolved = address.getHostAddress() + ":" + properties.getDefaultPort();
            log.debug("Docker resolved {} -> {}", serviceName, resolved);
            return resolved;
        } catch (Exception e) {
            log.warn("Docker DNS resolution failed for {}: {}. Using service name directly.", serviceName, e.getMessage());
            return serviceName + ":" + properties.getDefaultPort();
        }
    }

    /**
     * Static resolution from the in-memory registry.
     * Falls back to serviceName:defaultPort if not registered.
     */
    private String resolveStatic(String serviceName) {
        String resolved = staticRegistry.get(serviceName);
        if (resolved != null) {
            log.debug("Static resolved {} -> {}", serviceName, resolved);
            return resolved;
        }

        log.warn("No static mapping for service: {}. Using default: {}:{}",
                serviceName, serviceName, properties.getDefaultPort());
        return serviceName + ":" + properties.getDefaultPort();
    }
}
