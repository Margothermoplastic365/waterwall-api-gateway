package com.gateway.runtime.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Applies body-level transformations for JSON payloads.
 * Supports JSON-to-JSON field mapping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BodyTransformer {

    private final ObjectMapper objectMapper;

    /**
     * Apply JSON-to-JSON field mapping rules.
     * Each rule maps a source field path to a target field path.
     *
     * @param body         the raw JSON body bytes
     * @param mappingRules list of maps with "source" and "target" keys
     * @return transformed JSON bytes
     */
    public byte[] jsonToJson(byte[] body, List<Map<String, String>> mappingRules) {
        if (body == null || body.length == 0 || mappingRules == null || mappingRules.isEmpty()) {
            return body;
        }

        try {
            JsonNode source = objectMapper.readTree(body);
            ObjectNode target = objectMapper.createObjectNode();

            for (Map<String, String> rule : mappingRules) {
                String sourcePath = rule.get("source");
                String targetPath = rule.get("target");
                if (sourcePath == null || targetPath == null) continue;

                JsonNode value = source.at("/" + sourcePath.replace(".", "/"));
                if (!value.isMissingNode()) {
                    target.set(targetPath, value);
                }
            }

            return objectMapper.writeValueAsBytes(target);
        } catch (Exception e) {
            log.error("Failed to apply JSON-to-JSON transformation: {}", e.getMessage());
            return body;
        }
    }
}
