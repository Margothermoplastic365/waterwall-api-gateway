package com.gateway.management.service;

import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.FederatedGatewayEntity;
import com.gateway.management.entity.WorkspaceEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.entity.enums.Visibility;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.FederatedGatewayRepository;
import com.gateway.management.repository.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FederationService {

    private final FederatedGatewayRepository federatedGatewayRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ApiRepository apiRepository;

    private final RestClient.Builder restClientBuilder;

    // ── Federated Gateways CRUD ───────────────────────────────────────────

    @Transactional
    public FederatedGatewayEntity registerGateway(FederatedGatewayEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus("ACTIVE");
        }
        entity = federatedGatewayRepository.save(entity);
        log.info("Registered federated gateway: id={} name={} type={}", entity.getId(), entity.getName(), entity.getType());
        return entity;
    }

    public List<FederatedGatewayEntity> listGateways() {
        return federatedGatewayRepository.findAll();
    }

    public FederatedGatewayEntity getGateway(UUID id) {
        return federatedGatewayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Federated gateway not found: " + id));
    }

    // ── Sync APIs from external gateway (real implementation) ─────────────

    @Transactional
    public List<Map<String, Object>> syncApis(UUID gatewayId) {
        FederatedGatewayEntity gw = getGateway(gatewayId);
        String gatewayType = gw.getType() != null ? gw.getType().toUpperCase() : "";

        List<Map<String, Object>> discoveredApis;
        String syncError = null;

        try {
            discoveredApis = switch (gatewayType) {
                case "KONG" -> syncFromKong(gw);
                case "NGINX" -> syncFromNginx(gw);
                case "SELF", "CUSTOM" -> syncFromSelfGateway(gw);
                case "AWS_API_GATEWAY" -> syncFromAwsApiGateway(gw);
                default -> {
                    log.warn("Unsupported gateway type '{}' for gateway id={}. Returning empty result.", gatewayType, gatewayId);
                    yield Collections.emptyList();
                }
            };
        } catch (RestClientException e) {
            syncError = e.getMessage();
            log.error("Failed to sync APIs from gateway id={} name={} type={}: {}",
                    gatewayId, gw.getName(), gatewayType, e.getMessage());
            discoveredApis = Collections.emptyList();
        }

        // Persist discovered APIs as local entities
        List<ApiEntity> savedApis = persistDiscoveredApis(discoveredApis, gw);

        // Update gateway sync metadata
        gw.setLastSyncAt(Instant.now());
        if (syncError != null) {
            gw.setStatus("SYNC_ERROR");
        } else {
            gw.setStatus("ACTIVE");
        }
        federatedGatewayRepository.save(gw);

        // Build result list with sync metadata
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < discoveredApis.size(); i++) {
            Map<String, Object> entry = new LinkedHashMap<>(discoveredApis.get(i));
            if (i < savedApis.size()) {
                entry.put("localApiId", savedApis.get(i).getId());
            }
            entry.put("syncedAt", gw.getLastSyncAt().toString());
            if (syncError != null) {
                entry.put("syncError", syncError);
            }
            result.add(entry);
        }

        log.info("Synced {} APIs from federated gateway: id={} name={} type={}, errors={}",
                discoveredApis.size(), gatewayId, gw.getName(), gatewayType,
                syncError != null ? syncError : "none");

        return result;
    }

    // ── Kong sync ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> syncFromKong(FederatedGatewayEntity gw) {
        RestClient client = buildRestClient(gw);
        String baseUrl = normalizeUrl(gw.getApiUrl());

        // Kong 1.x+ uses /services; older versions use /apis — try /services first
        Map<String, Object> response;
        try {
            response = client.get()
                    .uri(baseUrl + "/services")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            log.debug("Kong /services endpoint failed, falling back to /apis: {}", e.getMessage());
            response = client.get()
                    .uri(baseUrl + "/apis")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        }

        if (response == null || !response.containsKey("data")) {
            log.warn("Kong response from {} contained no 'data' field", gw.getName());
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null) {
            return Collections.emptyList();
        }

        return data.stream()
                .map(svc -> {
                    Map<String, Object> api = new LinkedHashMap<>();
                    api.put("name", Objects.toString(svc.getOrDefault("name", svc.get("id")), "unknown"));
                    api.put("version", "v1");
                    api.put("source", gw.getName());
                    api.put("type", gw.getType());
                    api.put("protocol", Objects.toString(svc.getOrDefault("protocol", "http"), "http"));
                    api.put("host", svc.getOrDefault("host", ""));
                    api.put("path", svc.getOrDefault("path", svc.getOrDefault("request_path", "")));
                    api.put("externalId", Objects.toString(svc.get("id"), ""));
                    return api;
                })
                .collect(Collectors.toList());
    }

    // ── NGINX sync ────────────────────────────────────────────────────────

    private List<Map<String, Object>> syncFromNginx(FederatedGatewayEntity gw) {
        RestClient client = buildRestClient(gw);
        String baseUrl = normalizeUrl(gw.getApiUrl());

        // Expect an NGINX config API (e.g., NGINX Plus /api or custom management endpoint)
        // that returns upstream/server data as JSON
        Map<String, Object> response;
        try {
            response = client.get()
                    .uri(baseUrl + "/api/6/http/upstreams")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            log.debug("NGINX Plus API not available at /api/6/http/upstreams, trying /config: {}", e.getMessage());
            // Fallback: try a custom config endpoint that returns JSON route list
            try {
                List<Map<String, Object>> routes = client.get()
                        .uri(baseUrl + "/config")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                if (routes != null) {
                    return routes.stream()
                            .map(r -> {
                                Map<String, Object> api = new LinkedHashMap<>();
                                api.put("name", Objects.toString(r.getOrDefault("name", r.get("server_name")), "unknown"));
                                api.put("version", "v1");
                                api.put("source", gw.getName());
                                api.put("type", gw.getType());
                                api.put("path", Objects.toString(r.getOrDefault("location", r.get("path")), "/"));
                                api.put("externalId", Objects.toString(r.getOrDefault("id", ""), ""));
                                return api;
                            })
                            .collect(Collectors.toList());
                }
            } catch (RestClientException ex) {
                log.warn("NGINX config endpoint also unavailable for gateway {}: {}", gw.getName(), ex.getMessage());
            }
            return Collections.emptyList();
        }

        if (response == null) {
            return Collections.emptyList();
        }

        // NGINX Plus returns upstreams as a map of name -> upstream-object
        return response.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> api = new LinkedHashMap<>();
                    api.put("name", entry.getKey());
                    api.put("version", "v1");
                    api.put("source", gw.getName());
                    api.put("type", gw.getType());
                    api.put("path", "/" + entry.getKey());
                    api.put("externalId", entry.getKey());
                    return api;
                })
                .collect(Collectors.toList());
    }

    // ── SELF / CUSTOM gateway sync ────────────────────────────────────────

    private List<Map<String, Object>> syncFromSelfGateway(FederatedGatewayEntity gw) {
        RestClient client = buildRestClient(gw);
        String baseUrl = normalizeUrl(gw.getApiUrl());

        // Another instance of this same gateway platform, exposing /v1/apis
        List<Map<String, Object>> apis = client.get()
                .uri(baseUrl + "/v1/apis")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (apis == null) {
            return Collections.emptyList();
        }

        return apis.stream()
                .map(remote -> {
                    Map<String, Object> api = new LinkedHashMap<>();
                    api.put("name", Objects.toString(remote.getOrDefault("name", "unknown"), "unknown"));
                    api.put("version", Objects.toString(remote.getOrDefault("version", "v1"), "v1"));
                    api.put("source", gw.getName());
                    api.put("type", gw.getType());
                    api.put("description", remote.getOrDefault("description", ""));
                    api.put("status", remote.getOrDefault("status", "PUBLISHED"));
                    api.put("externalId", Objects.toString(remote.getOrDefault("id", ""), ""));
                    return api;
                })
                .collect(Collectors.toList());
    }

    // ── AWS API Gateway sync ──────────────────────────────────────────────

    private List<Map<String, Object>> syncFromAwsApiGateway(FederatedGatewayEntity gw) {
        // TODO: Implement AWS API Gateway sync using AWS SDK (software.amazon.awssdk:apigateway).
        //       This requires:
        //       1. Add aws-sdk-java-v2 apigateway dependency to pom.xml
        //       2. Build ApiGatewayClient using credentials from gw.getApiKeyEncrypted()
        //          (which should store the AWS access key / secret or assume-role ARN)
        //       3. Call getRestApis() and getResources() to discover APIs and their methods
        //       4. Map each REST API + resource to the standard discovered-API format
        //
        //       Example skeleton:
        //       ApiGatewayClient awsClient = ApiGatewayClient.builder()
        //           .region(Region.of(...))
        //           .credentialsProvider(StaticCredentialsProvider.create(
        //               AwsBasicCredentials.create(accessKey, secretKey)))
        //           .build();
        //       GetRestApisResponse restApis = awsClient.getRestApis(GetRestApisRequest.builder().build());
        //       for (RestApi restApi : restApis.items()) { ... }
        log.info("AWS API Gateway sync is not yet implemented for gateway id={} name={}. " +
                "Returning empty result.", gw.getId(), gw.getName());
        return Collections.emptyList();
    }

    // ── Persist discovered APIs locally ───────────────────────────────────

    private List<ApiEntity> persistDiscoveredApis(List<Map<String, Object>> discoveredApis, FederatedGatewayEntity gw) {
        List<ApiEntity> saved = new ArrayList<>();
        for (Map<String, Object> disc : discoveredApis) {
            String name = Objects.toString(disc.getOrDefault("name", "unknown"), "unknown");
            String version = Objects.toString(disc.getOrDefault("version", "v1"), "v1");
            String description = Objects.toString(disc.getOrDefault("description", ""), "");

            ApiEntity api = ApiEntity.builder()
                    .name(name)
                    .version(version)
                    .description(description.isEmpty()
                            ? "Synced from federated gateway: " + gw.getName()
                            : description)
                    .status(ApiStatus.CREATED)
                    .visibility(Visibility.PRIVATE)
                    .protocolType(Objects.toString(disc.getOrDefault("protocol", "REST"), "REST"))
                    .category("federated")
                    .tags(List.of("federated", "source:" + gw.getName(), "type:" + gw.getType()))
                    .build();

            saved.add(apiRepository.save(api));
        }
        return saved;
    }

    // ── Build a RestClient for the given gateway ──────────────────────────

    private RestClient buildRestClient(FederatedGatewayEntity gw) {
        RestClient.Builder builder = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // If the gateway has an API key configured, attach it as a header
        if (gw.getApiKeyEncrypted() != null && !gw.getApiKeyEncrypted().isBlank()) {
            builder.defaultHeader("apikey", gw.getApiKeyEncrypted());
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gw.getApiKeyEncrypted());
        }

        return builder.build();
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ── Federated Catalog ─────────────────────────────────────────────────

    public List<Map<String, Object>> getFederatedCatalog() {
        // Local APIs
        List<Map<String, Object>> catalog = apiRepository.findAll().stream()
                .map(api -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", api.getId());
                    entry.put("name", api.getName());
                    entry.put("version", api.getVersion());
                    entry.put("source", "LOCAL");
                    entry.put("type", "SELF");
                    return entry;
                })
                .collect(Collectors.toList());

        // Include externally synced APIs (tagged as federated) already in local store
        // They will naturally appear in the catalog above since they are persisted as ApiEntity.
        // Additionally, show any active gateways that have not been synced yet as placeholders.
        List<FederatedGatewayEntity> gateways = federatedGatewayRepository.findByStatus("ACTIVE");
        for (FederatedGatewayEntity gw : gateways) {
            if (gw.getLastSyncAt() == null) {
                catalog.add(Map.of(
                        "name", "[pending-sync] " + gw.getName(),
                        "version", "unknown",
                        "source", gw.getName(),
                        "type", gw.getType(),
                        "hint", "Call POST /v1/federation/gateways/" + gw.getId() + "/sync to discover APIs"
                ));
            }
        }

        return catalog;
    }

    // ── Workspaces CRUD ───────────────────────────────────────────────────

    @Transactional
    public WorkspaceEntity createWorkspace(WorkspaceEntity entity) {
        entity = workspaceRepository.save(entity);
        log.info("Created workspace: id={} slug={}", entity.getId(), entity.getSlug());
        return entity;
    }

    public List<WorkspaceEntity> listWorkspaces() {
        return workspaceRepository.findAll();
    }

    public WorkspaceEntity getWorkspace(UUID id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Workspace not found: " + id));
    }
}
