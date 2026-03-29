package com.gateway.runtime.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates event message payloads against registered JSON schemas.
 *
 * <p>Schemas are keyed by exchange (or topic) name.  When no schema is
 * registered for a given exchange the message is accepted as-is.</p>
 */
@Slf4j
@Component
public class EventSchemaValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** exchange/topic → JSON schema (as raw JSON string) */
    private final ConcurrentHashMap<String, String> schemaRegistry = new ConcurrentHashMap<>();

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Register a JSON schema for a given exchange/topic.
     */
    public void registerSchema(String exchangeOrTopic, String jsonSchema) {
        schemaRegistry.put(exchangeOrTopic, jsonSchema);
        log.info("Registered JSON schema for topic/exchange={}", exchangeOrTopic);
    }

    /**
     * Remove a registered schema.
     */
    public void removeSchema(String exchangeOrTopic) {
        schemaRegistry.remove(exchangeOrTopic);
    }

    /**
     * Validate a message payload against the schema registered for the
     * given exchange/topic.
     *
     * @return a {@link ValidationResult} — always valid when no schema is registered.
     */
    public ValidationResult validate(String exchangeOrTopic, String messagePayload) {
        String schema = schemaRegistry.get(exchangeOrTopic);
        if (schema == null) {
            // No schema registered — pass through
            return ValidationResult.valid();
        }

        List<String> errors = new ArrayList<>();

        try {
            JsonNode schemaNode = objectMapper.readTree(schema);
            JsonNode messageNode = objectMapper.readTree(messagePayload);

            // Validate required fields
            JsonNode requiredNode = schemaNode.get("required");
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode field : requiredNode) {
                    String fieldName = field.asText();
                    if (!messageNode.has(fieldName) || messageNode.get(fieldName).isNull()) {
                        errors.add("Missing required field: " + fieldName);
                    }
                }
            }

            // Validate field types from "properties"
            JsonNode propertiesNode = schemaNode.get("properties");
            if (propertiesNode != null) {
                propertiesNode.fields().forEachRemaining(entry -> {
                    String fieldName = entry.getKey();
                    JsonNode fieldSchema = entry.getValue();
                    JsonNode fieldValue = messageNode.get(fieldName);

                    if (fieldValue != null && !fieldValue.isNull()) {
                        String expectedType = fieldSchema.has("type") ? fieldSchema.get("type").asText() : null;
                        if (expectedType != null) {
                            String actualType = getJsonType(fieldValue);
                            if (!expectedType.equals(actualType)) {
                                errors.add("Field '" + fieldName + "' expected type '" + expectedType
                                        + "' but got '" + actualType + "'");
                            }
                        }
                    }
                });
            }

        } catch (Exception e) {
            errors.add("Failed to parse message as JSON: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(errors);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String getJsonType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong()) return "integer";
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "unknown";
    }

    // ── Result DTO ───────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;

        public static ValidationResult valid() {
            return ValidationResult.builder().valid(true).errors(List.of()).build();
        }

        public static ValidationResult invalid(List<String> errors) {
            return ValidationResult.builder().valid(false).errors(errors).build();
        }
    }
}
