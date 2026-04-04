package com.gateway.runtime.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BodyTransformerTest {

    private BodyTransformer bodyTransformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        bodyTransformer = new BodyTransformer(objectMapper);
    }

    @Test
    void shouldMapJsonFields() throws Exception {
        byte[] input = "{\"name\":\"John\",\"age\":30}".getBytes();
        List<Map<String, String>> rules = List.of(
                Map.of("source", "name", "target", "fullName")
        );

        byte[] result = bodyTransformer.jsonToJson(input, rules);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.get("fullName").asText()).isEqualTo("John");
        assertThat(resultNode.has("name")).isFalse();
        assertThat(resultNode.has("age")).isFalse();
    }

    @Test
    void shouldHandleNestedJsonPaths() throws Exception {
        byte[] input = "{\"address\":{\"city\":\"NYC\",\"zip\":\"10001\"}}".getBytes();
        List<Map<String, String>> rules = List.of(
                Map.of("source", "address.city", "target", "city")
        );

        byte[] result = bodyTransformer.jsonToJson(input, rules);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.get("city").asText()).isEqualTo("NYC");
    }

    @Test
    void shouldReturnEmptyObjectWhenNoMatchingFields() throws Exception {
        byte[] input = "{\"name\":\"John\"}".getBytes();
        List<Map<String, String>> rules = List.of(
                Map.of("source", "nonexistent", "target", "mapped")
        );

        byte[] result = bodyTransformer.jsonToJson(input, rules);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleEmptyBody() {
        byte[] result = bodyTransformer.jsonToJson(null, List.of(Map.of("source", "a", "target", "b")));
        assertThat(result).isNull();

        byte[] emptyResult = bodyTransformer.jsonToJson(new byte[0], List.of(Map.of("source", "a", "target", "b")));
        assertThat(emptyResult).isEmpty();
    }

    @Test
    void shouldReturnOriginalWhenRulesEmpty() {
        byte[] input = "{\"name\":\"John\"}".getBytes();

        byte[] result = bodyTransformer.jsonToJson(input, null);
        assertThat(result).isEqualTo(input);

        result = bodyTransformer.jsonToJson(input, List.of());
        assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldMapMultipleFields() throws Exception {
        byte[] input = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":30}".getBytes();
        List<Map<String, String>> rules = List.of(
                Map.of("source", "firstName", "target", "first"),
                Map.of("source", "lastName", "target", "last")
        );

        byte[] result = bodyTransformer.jsonToJson(input, rules);

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.get("first").asText()).isEqualTo("John");
        assertThat(resultNode.get("last").asText()).isEqualTo("Doe");
        assertThat(resultNode.has("age")).isFalse();
    }
}
