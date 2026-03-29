package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.management.dto.MockConfigRequest;
import com.gateway.management.dto.MockConfigResponse;
import com.gateway.management.dto.MockResponse;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.entity.MockConfigEntity;
import com.gateway.management.repository.ApiSpecRepository;
import com.gateway.management.repository.MockConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockingService {

    private final MockConfigRepository mockConfigRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate a mock response for the given API, path, and method.
     * Attempts to find a configured mock first, then falls back to generating from spec.
     */
    public MockResponse generateMock(UUID apiId, String path, String method) {
        // Check for explicit mock config
        Optional<MockConfigEntity> configOpt = mockConfigRepository
                .findByApiIdAndPathAndMethod(apiId, path, method.toUpperCase());

        if (configOpt.isPresent()) {
            MockConfigEntity config = configOpt.get();

            // Error simulation
            if (config.getErrorRatePercent() > 0) {
                int roll = ThreadLocalRandom.current().nextInt(100);
                if (roll < config.getErrorRatePercent()) {
                    return MockResponse.builder()
                            .statusCode(500)
                            .headers(Map.of("X-Mock", "true", "X-Mock-Error", "simulated"))
                            .body("{\"error\":\"Simulated error\",\"message\":\"Mock error simulation triggered\"}")
                            .contentType("application/json")
                            .build();
                }
            }

            return MockResponse.builder()
                    .statusCode(config.getStatusCode())
                    .headers(config.getResponseHeaders() != null ? config.getResponseHeaders() : Map.of())
                    .body(config.getResponseBody())
                    .contentType("application/json")
                    .build();
        }

        // Fall back to generating from API spec
        return generateFromSpec(apiId, path, method);
    }

    /**
     * Generate a mock response by parsing the OpenAPI spec.
     */
    private MockResponse generateFromSpec(UUID apiId, String path, String method) {
        Optional<ApiSpecEntity> specOpt = apiSpecRepository.findByApiId(apiId);
        if (specOpt.isEmpty()) {
            return MockResponse.builder()
                    .statusCode(200)
                    .headers(Map.of("X-Mock", "true"))
                    .body("{\"message\":\"Mock response (no spec available)\"}")
                    .contentType("application/json")
                    .build();
        }

        try {
            JsonNode spec = objectMapper.readTree(specOpt.get().getSpecContent());
            JsonNode paths = spec.get("paths");
            if (paths == null) {
                return defaultMockResponse();
            }

            // Try to find the matching path operation
            JsonNode pathNode = paths.get(path);
            if (pathNode == null) {
                // Try matching path templates
                pathNode = findMatchingPath(paths, path);
            }
            if (pathNode == null) {
                return defaultMockResponse();
            }

            JsonNode operationNode = pathNode.get(method.toLowerCase());
            if (operationNode == null) {
                return defaultMockResponse();
            }

            // Look for response examples
            JsonNode responses = operationNode.get("responses");
            if (responses == null) {
                return defaultMockResponse();
            }

            // Prefer 200, then 201, then first 2xx
            JsonNode responseNode = responses.get("200");
            if (responseNode == null) responseNode = responses.get("201");
            if (responseNode == null) {
                for (Iterator<Map.Entry<String, JsonNode>> it = responses.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    if (entry.getKey().startsWith("2")) {
                        responseNode = entry.getValue();
                        break;
                    }
                }
            }

            if (responseNode == null) {
                return defaultMockResponse();
            }

            // Try to get example from content -> application/json -> schema/example
            String body = extractExampleBody(responseNode, spec);

            int statusCode = 200;
            return MockResponse.builder()
                    .statusCode(statusCode)
                    .headers(Map.of("X-Mock", "true", "X-Mock-Source", "spec"))
                    .body(body)
                    .contentType("application/json")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to generate mock from spec for apiId={}: {}", apiId, e.getMessage());
            return defaultMockResponse();
        }
    }

    private JsonNode findMatchingPath(JsonNode paths, String requestPath) {
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String pattern = entry.getKey();
            if (pathMatches(pattern, requestPath)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean pathMatches(String pattern, String requestPath) {
        String[] patternParts = pattern.split("/");
        String[] requestParts = requestPath.split("/");
        if (patternParts.length != requestParts.length) return false;
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                continue;
            }
            if (!patternParts[i].equals(requestParts[i])) {
                return false;
            }
        }
        return true;
    }

    private String extractExampleBody(JsonNode responseNode, JsonNode spec) {
        try {
            JsonNode content = responseNode.get("content");
            if (content != null) {
                JsonNode jsonContent = content.get("application/json");
                if (jsonContent != null) {
                    // Check for example
                    JsonNode example = jsonContent.get("example");
                    if (example != null) {
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
                    }
                    // Check for examples (plural)
                    JsonNode examples = jsonContent.get("examples");
                    if (examples != null && examples.fields().hasNext()) {
                        JsonNode firstExample = examples.fields().next().getValue();
                        JsonNode value = firstExample.get("value");
                        if (value != null) {
                            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
                        }
                    }
                    // Generate from schema
                    JsonNode schema = jsonContent.get("schema");
                    if (schema != null) {
                        return objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(generateDynamicFromSchema(schema, spec));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract example body: {}", e.getMessage());
        }
        return "{\"message\":\"Mock response generated from spec\"}";
    }

    /**
     * Generate dynamic data matching the schema types.
     */
    private JsonNode generateDynamicFromSchema(JsonNode schema, JsonNode spec) {
        // Resolve $ref if present
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            schema = resolveRef(ref, spec);
            if (schema == null) {
                return objectMapper.createObjectNode().put("mock", true);
            }
        }

        String type = schema.has("type") ? schema.get("type").asText() : "object";

        switch (type) {
            case "object": {
                ObjectNode obj = objectMapper.createObjectNode();
                JsonNode properties = schema.get("properties");
                if (properties != null) {
                    for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
                        Map.Entry<String, JsonNode> entry = it.next();
                        obj.set(entry.getKey(), generateDynamicFromSchema(entry.getValue(), spec));
                    }
                }
                return obj;
            }
            case "array": {
                ArrayNode arr = objectMapper.createArrayNode();
                JsonNode items = schema.get("items");
                if (items != null) {
                    arr.add(generateDynamicFromSchema(items, spec));
                }
                return arr;
            }
            case "string": {
                String format = schema.has("format") ? schema.get("format").asText() : "";
                if (schema.has("example")) return schema.get("example");
                if (schema.has("enum")) return schema.get("enum").get(0);
                return switch (format) {
                    case "date-time" -> objectMapper.getNodeFactory().textNode("2026-01-01T00:00:00Z");
                    case "date" -> objectMapper.getNodeFactory().textNode("2026-01-01");
                    case "email" -> objectMapper.getNodeFactory().textNode("user@example.com");
                    case "uuid" -> objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString());
                    case "uri", "url" -> objectMapper.getNodeFactory().textNode("https://example.com");
                    default -> objectMapper.getNodeFactory().textNode("mock-string");
                };
            }
            case "integer": {
                if (schema.has("example")) return schema.get("example");
                return objectMapper.getNodeFactory().numberNode(ThreadLocalRandom.current().nextInt(1, 1000));
            }
            case "number": {
                if (schema.has("example")) return schema.get("example");
                return objectMapper.getNodeFactory().numberNode(
                        Math.round(ThreadLocalRandom.current().nextDouble(1, 1000) * 100.0) / 100.0);
            }
            case "boolean": {
                if (schema.has("example")) return schema.get("example");
                return objectMapper.getNodeFactory().booleanNode(true);
            }
            default:
                return objectMapper.getNodeFactory().textNode("mock-value");
        }
    }

    private JsonNode resolveRef(String ref, JsonNode spec) {
        // Handle "#/components/schemas/Foo"
        if (ref.startsWith("#/")) {
            String[] parts = ref.substring(2).split("/");
            JsonNode current = spec;
            for (String part : parts) {
                if (current == null) return null;
                current = current.get(part);
            }
            return current;
        }
        return null;
    }

    private MockResponse defaultMockResponse() {
        return MockResponse.builder()
                .statusCode(200)
                .headers(Map.of("X-Mock", "true"))
                .body("{\"message\":\"Mock response\"}")
                .contentType("application/json")
                .build();
    }

    // ── Mock Mode Management ─────────────────────────────────────────────

    @Transactional
    public void setMockMode(UUID apiId, boolean enabled) {
        List<MockConfigEntity> configs = mockConfigRepository.findByApiId(apiId);
        if (configs.isEmpty()) {
            // Create a default mock config entry
            MockConfigEntity defaultConfig = MockConfigEntity.builder()
                    .apiId(apiId)
                    .path("/**")
                    .method("*")
                    .statusCode(200)
                    .responseBody("{\"message\":\"Default mock response\"}")
                    .mockEnabled(enabled)
                    .build();
            mockConfigRepository.save(defaultConfig);
        } else {
            for (MockConfigEntity config : configs) {
                config.setMockEnabled(enabled);
            }
            mockConfigRepository.saveAll(configs);
        }
        log.info("Mock mode {} for apiId={}", enabled ? "enabled" : "disabled", apiId);
    }

    public boolean isMockEnabled(UUID apiId) {
        return mockConfigRepository.existsByApiIdAndMockEnabledTrue(apiId);
    }

    // ── Mock Config CRUD ─────────────────────────────────────────────────

    public List<MockConfigResponse> getConfigs(UUID apiId) {
        return mockConfigRepository.findByApiId(apiId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MockConfigResponse addConfig(UUID apiId, MockConfigRequest request) {
        MockConfigEntity entity = MockConfigEntity.builder()
                .apiId(apiId)
                .path(request.getPath())
                .method(request.getMethod() != null ? request.getMethod().toUpperCase() : "*")
                .statusCode(request.getStatusCode() > 0 ? request.getStatusCode() : 200)
                .responseBody(request.getResponseBody())
                .responseHeaders(request.getResponseHeaders())
                .latencyMs(request.getLatencyMs())
                .errorRatePercent(request.getErrorRatePercent())
                .mockEnabled(true)
                .build();
        entity = mockConfigRepository.save(entity);
        log.info("Added mock config {} for apiId={}", entity.getId(), apiId);
        return toResponse(entity);
    }

    @Transactional
    public void deleteConfig(UUID apiId, UUID configId) {
        MockConfigEntity entity = mockConfigRepository.findById(configId)
                .orElseThrow(() -> new EntityNotFoundException("Mock config not found: " + configId));
        if (!entity.getApiId().equals(apiId)) {
            throw new IllegalArgumentException("Mock config does not belong to API: " + apiId);
        }
        mockConfigRepository.delete(entity);
        log.info("Deleted mock config {} for apiId={}", configId, apiId);
    }

    private MockConfigResponse toResponse(MockConfigEntity entity) {
        return MockConfigResponse.builder()
                .id(entity.getId())
                .apiId(entity.getApiId())
                .path(entity.getPath())
                .method(entity.getMethod())
                .statusCode(entity.getStatusCode())
                .responseBody(entity.getResponseBody())
                .responseHeaders(entity.getResponseHeaders())
                .latencyMs(entity.getLatencyMs())
                .errorRatePercent(entity.getErrorRatePercent())
                .mockEnabled(entity.isMockEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
