package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-assisted platform operations service.
 * Implements pattern-based helpers (not actual LLM calls) for the MVP:
 * <ul>
 *   <li>Generate OpenAPI spec from natural language description</li>
 *   <li>Suggest policy configuration from description</li>
 *   <li>Detect issues in an OpenAPI spec</li>
 *   <li>Generate mock responses from an OpenAPI spec</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final ObjectMapper objectMapper;

    // ── 1. Generate OpenAPI spec from description ───────────────────────────────

    /**
     * Parse a natural language description and generate a basic OpenAPI 3.0 skeleton.
     * Example input: "Create a REST API for managing orders with CRUD operations"
     */
    public String generateSpecFromDescription(String description) {
        log.info("Generating OpenAPI spec from description: {}", description);

        String resourceName = extractResourceName(description);
        String resourceNameLower = resourceName.toLowerCase();
        String resourceNameCapitalized = capitalize(resourceName);

        ObjectNode spec = objectMapper.createObjectNode();
        spec.put("openapi", "3.0.3");

        // Info
        ObjectNode info = spec.putObject("info");
        info.put("title", resourceNameCapitalized + " API");
        info.put("description", "Auto-generated API for managing " + resourceNameLower + "s");
        info.put("version", "1.0.0");

        // Paths
        ObjectNode paths = spec.putObject("paths");

        // Collection path: /{resources}
        String collectionPath = "/" + resourceNameLower + "s";
        ObjectNode collectionOps = paths.putObject(collectionPath);

        // GET collection
        ObjectNode getAll = collectionOps.putObject("get");
        getAll.put("summary", "List all " + resourceNameLower + "s");
        getAll.put("operationId", "list" + resourceNameCapitalized + "s");
        ObjectNode getResponses = getAll.putObject("responses");
        ObjectNode get200 = getResponses.putObject("200");
        get200.put("description", "Successful response");
        ObjectNode get200Content = get200.putObject("content").putObject("application/json").putObject("schema");
        get200Content.put("type", "array");
        get200Content.putObject("items").put("$ref", "#/components/schemas/" + resourceNameCapitalized);

        // POST collection
        ObjectNode post = collectionOps.putObject("post");
        post.put("summary", "Create a new " + resourceNameLower);
        post.put("operationId", "create" + resourceNameCapitalized);
        ObjectNode postBody = post.putObject("requestBody");
        postBody.put("required", true);
        postBody.putObject("content").putObject("application/json").putObject("schema")
                .put("$ref", "#/components/schemas/" + resourceNameCapitalized);
        ObjectNode postResponses = post.putObject("responses");
        postResponses.putObject("201").put("description", resourceNameCapitalized + " created");

        // Item path: /{resources}/{id}
        String itemPath = collectionPath + "/{id}";
        ObjectNode itemOps = paths.putObject(itemPath);

        // GET item
        ObjectNode getOne = itemOps.putObject("get");
        getOne.put("summary", "Get " + resourceNameLower + " by ID");
        getOne.put("operationId", "get" + resourceNameCapitalized);
        addIdParam(getOne);
        ObjectNode getOneResponses = getOne.putObject("responses");
        ObjectNode getOne200 = getOneResponses.putObject("200");
        getOne200.put("description", "Successful response");
        getOne200.putObject("content").putObject("application/json").putObject("schema")
                .put("$ref", "#/components/schemas/" + resourceNameCapitalized);
        getOneResponses.putObject("404").put("description", resourceNameCapitalized + " not found");

        // PUT item
        ObjectNode put = itemOps.putObject("put");
        put.put("summary", "Update " + resourceNameLower);
        put.put("operationId", "update" + resourceNameCapitalized);
        addIdParam(put);
        ObjectNode putBody = put.putObject("requestBody");
        putBody.put("required", true);
        putBody.putObject("content").putObject("application/json").putObject("schema")
                .put("$ref", "#/components/schemas/" + resourceNameCapitalized);
        ObjectNode putResponses = put.putObject("responses");
        putResponses.putObject("200").put("description", resourceNameCapitalized + " updated");

        // DELETE item
        ObjectNode delete = itemOps.putObject("delete");
        delete.put("summary", "Delete " + resourceNameLower);
        delete.put("operationId", "delete" + resourceNameCapitalized);
        addIdParam(delete);
        ObjectNode deleteResponses = delete.putObject("responses");
        deleteResponses.putObject("204").put("description", resourceNameCapitalized + " deleted");

        // Components / Schemas
        ObjectNode components = spec.putObject("components");
        ObjectNode schemas = components.putObject("schemas");
        ObjectNode schema = schemas.putObject(resourceNameCapitalized);
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        // Generate default properties based on resource name
        properties.putObject("id").put("type", "string").put("format", "uuid");
        generateSchemaProperties(resourceNameLower, properties);
        properties.putObject("createdAt").put("type", "string").put("format", "date-time");

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (Exception e) {
            log.error("Failed to serialize OpenAPI spec", e);
            return "{}";
        }
    }

    // ── 2. Suggest policy from description ──────────────────────────────────────

    /**
     * Parse a natural language description and generate a policy JSON.
     * Example: "Rate limit free tier to 100 per minute"
     */
    public String suggestPolicy(String description) {
        log.info("Suggesting policy from description: {}", description);
        String lower = description.toLowerCase();

        ObjectNode policy = objectMapper.createObjectNode();

        if (lower.contains("rate limit") || lower.contains("ratelimit") || lower.contains("throttl")) {
            policy.put("type", "RATE_LIMIT");
            ObjectNode config = policy.putObject("config");

            int rate = extractNumber(lower, 100);
            String window = extractTimeWindow(lower);

            switch (window) {
                case "second" -> config.put("requestsPerSecond", rate);
                case "hour" -> config.put("requestsPerHour", rate);
                default -> config.put("requestsPerMinute", rate);
            }

            config.put("enforcement", lower.contains("hard") ? "HARD" : "SOFT");

        } else if (lower.contains("cors")) {
            policy.put("type", "CORS");
            ObjectNode config = policy.putObject("config");
            config.put("allowOrigins", lower.contains("all") ? "*" : "https://example.com");
            ArrayNode methods = config.putArray("allowMethods");
            methods.add("GET").add("POST").add("PUT").add("DELETE").add("OPTIONS");
            config.put("allowCredentials", true);
            config.put("maxAge", 3600);

        } else if (lower.contains("cach")) {
            policy.put("type", "CACHE");
            ObjectNode config = policy.putObject("config");
            int ttl = extractNumber(lower, 300);
            config.put("ttlSeconds", ttl);
            config.put("scope", "PUBLIC");

        } else if (lower.contains("auth") || lower.contains("jwt") || lower.contains("oauth")) {
            policy.put("type", "AUTHENTICATION");
            ObjectNode config = policy.putObject("config");
            if (lower.contains("jwt")) {
                config.put("method", "JWT");
                config.put("issuer", "https://auth.example.com");
            } else if (lower.contains("api key") || lower.contains("apikey")) {
                config.put("method", "API_KEY");
                config.put("headerName", "X-API-Key");
            } else {
                config.put("method", "OAUTH2");
                config.put("tokenEndpoint", "https://auth.example.com/oauth/token");
            }

        } else if (lower.contains("transform") || lower.contains("header")) {
            policy.put("type", "HEADER_TRANSFORM");
            ObjectNode config = policy.putObject("config");
            ObjectNode addHeaders = config.putObject("addHeaders");
            addHeaders.put("X-Gateway", "true");
            ArrayNode removeHeaders = config.putArray("removeHeaders");
            removeHeaders.add("X-Internal-Id");

        } else {
            policy.put("type", "CUSTOM");
            ObjectNode config = policy.putObject("config");
            config.put("description", description);
            config.put("enforcement", "SOFT");
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(policy);
        } catch (Exception e) {
            log.error("Failed to serialize policy", e);
            return "{}";
        }
    }

    // ── 3. Detect spec issues ───────────────────────────────────────────────────

    /**
     * Analyze an OpenAPI spec for common issues and lint rule violations.
     */
    public List<String> detectSpecIssues(String spec) {
        log.info("Detecting issues in OpenAPI spec ({} chars)", spec != null ? spec.length() : 0);
        List<String> issues = new ArrayList<>();

        if (spec == null || spec.isBlank()) {
            issues.add("ERROR: Spec is empty or null");
            return issues;
        }

        try {
            JsonNode root = objectMapper.readTree(spec);

            // Check openapi version
            JsonNode openapi = root.get("openapi");
            if (openapi == null || openapi.asText().isBlank()) {
                issues.add("ERROR: Missing 'openapi' version field");
            } else if (!openapi.asText().startsWith("3.")) {
                issues.add("WARNING: OpenAPI version " + openapi.asText() + " may not be fully supported");
            }

            // Check info
            JsonNode info = root.get("info");
            if (info == null) {
                issues.add("ERROR: Missing 'info' object");
            } else {
                if (info.get("title") == null || info.get("title").asText().isBlank()) {
                    issues.add("ERROR: Missing 'info.title'");
                }
                if (info.get("version") == null || info.get("version").asText().isBlank()) {
                    issues.add("WARNING: Missing 'info.version'");
                }
                if (info.get("description") == null) {
                    issues.add("INFO: Consider adding 'info.description' for better documentation");
                }
            }

            // Check paths
            JsonNode paths = root.get("paths");
            if (paths == null || paths.isEmpty()) {
                issues.add("ERROR: No paths defined");
            } else {
                paths.fieldNames().forEachRemaining(path -> {
                    if (!path.startsWith("/")) {
                        issues.add("ERROR: Path '" + path + "' must start with '/'");
                    }

                    JsonNode pathItem = paths.get(path);
                    pathItem.fieldNames().forEachRemaining(method -> {
                        JsonNode operation = pathItem.get(method);
                        if (operation.isObject()) {
                            // Check for operationId
                            if (operation.get("operationId") == null) {
                                issues.add("WARNING: Missing operationId for " + method.toUpperCase() + " " + path);
                            }
                            // Check for responses
                            if (operation.get("responses") == null || operation.get("responses").isEmpty()) {
                                issues.add("ERROR: Missing responses for " + method.toUpperCase() + " " + path);
                            }
                            // Check POST/PUT have request body
                            if (("post".equals(method) || "put".equals(method))
                                    && operation.get("requestBody") == null) {
                                issues.add("WARNING: " + method.toUpperCase() + " " + path
                                        + " should have a requestBody");
                            }
                        }
                    });
                });
            }

            // Check components/schemas
            JsonNode components = root.get("components");
            if (components == null || components.get("schemas") == null) {
                issues.add("INFO: No schemas defined in components — consider defining reusable schemas");
            }

            // Check for security definitions
            if (root.get("security") == null && (components == null || components.get("securitySchemes") == null)) {
                issues.add("INFO: No security scheme defined — consider adding authentication");
            }

        } catch (Exception e) {
            issues.add("ERROR: Failed to parse spec as JSON: " + e.getMessage());
        }

        if (issues.isEmpty()) {
            issues.add("OK: No issues detected");
        }

        return issues;
    }

    // ── 4. Generate mock from spec ──────────────────────────────────────────────

    /**
     * Generate mock response JSON from an OpenAPI spec's schemas.
     */
    public String generateMockFromSpec(String spec) {
        log.info("Generating mock from OpenAPI spec");

        try {
            JsonNode root = objectMapper.readTree(spec);
            JsonNode schemas = root.path("components").path("schemas");

            if (schemas.isMissingNode() || schemas.isEmpty()) {
                return objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Map.of("message", "No schemas found in spec"));
            }

            Map<String, Object> mocks = new LinkedHashMap<>();
            schemas.fieldNames().forEachRemaining(schemaName -> {
                JsonNode schema = schemas.get(schemaName);
                mocks.put(schemaName, generateMockForSchema(schema, schemas));
            });

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mocks);
        } catch (Exception e) {
            log.error("Failed to generate mock from spec", e);
            return "{\"error\": \"Failed to parse spec: " + e.getMessage() + "\"}";
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private String extractResourceName(String description) {
        String lower = description.toLowerCase();

        // Try to find "for managing <resource>"
        Pattern pattern = Pattern.compile("(?:for\\s+)?managing\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(lower);
        if (matcher.find()) {
            String name = matcher.group(1);
            // Remove trailing 's' for plural
            if (name.endsWith("s") && name.length() > 2) {
                name = name.substring(0, name.length() - 1);
            }
            return name;
        }

        // Try "REST API for <resource>"
        pattern = Pattern.compile("api\\s+for\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(lower);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (name.endsWith("s") && name.length() > 2) {
                name = name.substring(0, name.length() - 1);
            }
            return name;
        }

        // Fallback: use first noun-like word after common verbs
        pattern = Pattern.compile("(?:create|build|design|make)\\s+(?:a\\s+)?(?:rest\\s+)?(?:api\\s+)?(?:for\\s+)?(?:managing\\s+)?(\\w+)",
                Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(lower);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (name.endsWith("s") && name.length() > 2) {
                name = name.substring(0, name.length() - 1);
            }
            return name;
        }

        return "resource";
    }

    private void generateSchemaProperties(String resourceName, ObjectNode properties) {
        // Generate contextual properties based on resource type
        switch (resourceName) {
            case "order" -> {
                properties.putObject("status").put("type", "string")
                        .put("enum", "PENDING,CONFIRMED,SHIPPED,DELIVERED,CANCELLED");
                properties.putObject("totalAmount").put("type", "number").put("format", "double");
                properties.putObject("customerId").put("type", "string").put("format", "uuid");
            }
            case "product" -> {
                properties.putObject("name").put("type", "string");
                properties.putObject("description").put("type", "string");
                properties.putObject("price").put("type", "number").put("format", "double");
                properties.putObject("category").put("type", "string");
            }
            case "user" -> {
                properties.putObject("email").put("type", "string").put("format", "email");
                properties.putObject("name").put("type", "string");
                properties.putObject("role").put("type", "string");
            }
            case "customer" -> {
                properties.putObject("email").put("type", "string").put("format", "email");
                properties.putObject("name").put("type", "string");
                properties.putObject("phone").put("type", "string");
            }
            default -> {
                properties.putObject("name").put("type", "string");
                properties.putObject("description").put("type", "string");
                properties.putObject("status").put("type", "string");
            }
        }
    }

    private void addIdParam(ObjectNode operation) {
        ArrayNode params = operation.putArray("parameters");
        ObjectNode param = params.addObject();
        param.put("name", "id");
        param.put("in", "path");
        param.put("required", true);
        param.putObject("schema").put("type", "string").put("format", "uuid");
    }

    private int extractNumber(String text, int defaultValue) {
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String extractTimeWindow(String text) {
        if (text.contains("second")) return "second";
        if (text.contains("hour")) return "hour";
        return "minute";
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private Object generateMockForSchema(JsonNode schema, JsonNode allSchemas) {
        if (schema == null) return null;

        // Handle $ref
        JsonNode ref = schema.get("$ref");
        if (ref != null) {
            String refPath = ref.asText();
            String refName = refPath.substring(refPath.lastIndexOf('/') + 1);
            JsonNode refSchema = allSchemas.get(refName);
            return refSchema != null ? generateMockForSchema(refSchema, allSchemas) : Map.of();
        }

        String type = schema.has("type") ? schema.get("type").asText() : "object";

        return switch (type) {
            case "string" -> {
                String format = schema.has("format") ? schema.get("format").asText() : "";
                yield switch (format) {
                    case "uuid" -> "550e8400-e29b-41d4-a716-446655440000";
                    case "date-time" -> "2025-01-15T10:30:00Z";
                    case "date" -> "2025-01-15";
                    case "email" -> "user@example.com";
                    case "uri", "url" -> "https://example.com";
                    default -> "sample-string";
                };
            }
            case "integer" -> 42;
            case "number" -> 99.99;
            case "boolean" -> true;
            case "array" -> {
                JsonNode items = schema.get("items");
                List<Object> arr = new ArrayList<>();
                arr.add(generateMockForSchema(items, allSchemas));
                yield arr;
            }
            case "object" -> {
                Map<String, Object> obj = new LinkedHashMap<>();
                JsonNode properties = schema.get("properties");
                if (properties != null) {
                    properties.fieldNames().forEachRemaining(prop -> {
                        obj.put(prop, generateMockForSchema(properties.get(prop), allSchemas));
                    });
                }
                yield obj;
            }
            default -> "unknown-type";
        };
    }
}
