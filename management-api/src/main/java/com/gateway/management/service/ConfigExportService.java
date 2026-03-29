package com.gateway.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.gateway.management.dto.ConfigDiffResponse;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigExportService {

    private final ApiRepository apiRepository;
    private final RouteRepository routeRepository;
    private final ObjectMapper jsonMapper;

    public String exportConfig(UUID apiId, String format) {
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        List<RouteEntity> routes = routeRepository.findByApiId(apiId);

        Map<String, Object> config = buildApiConfig(api, routes);
        return serialize(config, format);
    }

    public String exportAll(String format) {
        List<ApiEntity> apis = apiRepository.findAll();
        List<Map<String, Object>> allConfigs = new ArrayList<>();

        for (ApiEntity api : apis) {
            List<RouteEntity> routes = routeRepository.findByApiId(api.getId());
            allConfigs.add(buildApiConfig(api, routes));
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0");
        root.put("apis", allConfigs);
        return serialize(root, format);
    }

    @Transactional
    public Map<String, Object> importConfig(String yaml) {
        try {
            ObjectMapper yamlMapper = createYamlMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yamlMapper.readValue(yaml, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apis = (List<Map<String, Object>>) root.getOrDefault("apis", List.of(root));

            int created = 0;
            int updated = 0;

            for (Map<String, Object> apiConfig : apis) {
                String name = (String) apiConfig.get("name");
                String version = (String) apiConfig.getOrDefault("version", "1.0.0");

                // Try to find existing API by name
                List<ApiEntity> existing = apiRepository.findAll().stream()
                        .filter(a -> a.getName().equals(name))
                        .toList();

                ApiEntity api;
                if (existing.isEmpty()) {
                    api = ApiEntity.builder()
                            .name(name)
                            .version(version)
                            .description((String) apiConfig.get("description"))
                            .status(ApiStatus.CREATED)
                            .protocolType((String) apiConfig.getOrDefault("protocolType", "REST"))
                            .build();
                    api = apiRepository.save(api);
                    created++;
                } else {
                    api = existing.get(0);
                    api.setVersion(version);
                    api.setDescription((String) apiConfig.get("description"));
                    api = apiRepository.save(api);
                    updated++;
                }

                // Import routes if present
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> routeConfigs =
                        (List<Map<String, Object>>) apiConfig.getOrDefault("routes", List.of());
                for (Map<String, Object> routeConfig : routeConfigs) {
                    RouteEntity route = RouteEntity.builder()
                            .api(api)
                            .path((String) routeConfig.get("path"))
                            .method((String) routeConfig.getOrDefault("method", "GET"))
                            .upstreamUrl((String) routeConfig.get("upstreamUrl"))
                            .enabled(true)
                            .build();
                    routeRepository.save(route);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("created", created);
            result.put("updated", updated);
            result.put("status", "SUCCESS");
            return result;
        } catch (Exception e) {
            log.error("Failed to import config: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid configuration YAML: " + e.getMessage());
        }
    }

    public ConfigDiffResponse diff(String yaml) {
        try {
            ObjectMapper yamlMapper = createYamlMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yamlMapper.readValue(yaml, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> importedApis =
                    (List<Map<String, Object>>) root.getOrDefault("apis", List.of(root));

            List<ApiEntity> existingApis = apiRepository.findAll();
            Set<String> existingNames = new HashSet<>();
            for (ApiEntity a : existingApis) {
                existingNames.add(a.getName());
            }

            List<String> details = new ArrayList<>();
            int added = 0, modified = 0;
            Set<String> importedNames = new HashSet<>();

            for (Map<String, Object> apiConfig : importedApis) {
                String name = (String) apiConfig.get("name");
                importedNames.add(name);
                if (existingNames.contains(name)) {
                    modified++;
                    details.add("MODIFIED: " + name);
                } else {
                    added++;
                    details.add("ADDED: " + name);
                }
            }

            int removed = 0;
            for (String existingName : existingNames) {
                if (!importedNames.contains(existingName)) {
                    removed++;
                    details.add("REMOVED: " + existingName);
                }
            }

            return ConfigDiffResponse.builder()
                    .addedCount(added)
                    .modifiedCount(modified)
                    .removedCount(removed)
                    .details(details)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid configuration YAML: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Map<String, Object> buildApiConfig(ApiEntity api, List<RouteEntity> routes) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", api.getName());
        config.put("version", api.getVersion());
        config.put("description", api.getDescription());
        config.put("status", api.getStatus() != null ? api.getStatus().name() : null);
        config.put("protocolType", api.getProtocolType());

        List<Map<String, Object>> routeList = new ArrayList<>();
        for (RouteEntity route : routes) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", route.getPath());
            r.put("method", route.getMethod());
            r.put("upstreamUrl", route.getUpstreamUrl());
            routeList.add(r);
        }
        config.put("routes", routeList);
        return config;
    }

    private String serialize(Object data, String format) {
        try {
            if ("json".equalsIgnoreCase(format)) {
                ObjectMapper prettyJson = jsonMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
                return prettyJson.writeValueAsString(data);
            } else {
                ObjectMapper yamlMapper = createYamlMapper();
                return yamlMapper.writeValueAsString(data);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config: " + e.getMessage(), e);
        }
    }

    private ObjectMapper createYamlMapper() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(factory)
                .findAndRegisterModules();
    }
}
