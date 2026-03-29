package com.gateway.runtime.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol proxy handler for JSON-RPC 2.0 requests.
 *
 * <p>Handles both single JSON-RPC requests and batch requests (array of calls).
 * Routes by the "method" field in the JSON-RPC payload. The actual proxying
 * uses the same RestClient as REST proxying.</p>
 */
@Component
@ConditionalOnProperty(name = "gateway.runtime.protocols.jsonrpc-enabled", havingValue = "true")
public class JsonRpcProxyHandler implements ProtocolProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcProxyHandler.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JsonRpcProxyHandler(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProtocolType() {
        return "JSONRPC";
    }

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute) {
        long startTime = System.currentTimeMillis();
        GatewayRoute route = matchedRoute.getRoute();

        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body == null || body.length == 0) {
                return buildJsonRpcError(null, -32700, "Parse error: empty request body");
            }

            JsonNode jsonNode = objectMapper.readTree(body);

            // Detect batch vs single request
            if (jsonNode.isArray()) {
                return handleBatchRequest((ArrayNode) jsonNode, route, startTime);
            } else if (jsonNode.isObject()) {
                return handleSingleRequest((ObjectNode) jsonNode, route, body, startTime);
            } else {
                return buildJsonRpcError(null, -32700, "Parse error: expected object or array");
            }

        } catch (IOException e) {
            log.error("Failed to read JSON-RPC request body: {}", e.getMessage());
            return buildJsonRpcError(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            log.error("JSON-RPC proxy error: {}", e.getMessage(), e);
            return buildJsonRpcError(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private ResponseEntity<byte[]> handleSingleRequest(ObjectNode jsonRpc, GatewayRoute route,
                                                         byte[] rawBody, long startTime) {
        String method = jsonRpc.has("method") ? jsonRpc.get("method").asText() : null;
        JsonNode id = jsonRpc.get("id");

        if (method == null || method.isBlank()) {
            return buildJsonRpcError(id, -32600, "Invalid Request: missing 'method' field");
        }

        String jsonrpcVersion = jsonRpc.has("jsonrpc") ? jsonRpc.get("jsonrpc").asText() : null;
        if (!"2.0".equals(jsonrpcVersion)) {
            log.warn("JSON-RPC request with non-2.0 version: {}", jsonrpcVersion);
        }

        log.info("JSON-RPC request: method={}, id={}", method, id);

        // Forward the entire request body to the upstream
        return forwardToUpstream(route.getUpstreamUrl(), rawBody, startTime);
    }

    private ResponseEntity<byte[]> handleBatchRequest(ArrayNode batchArray, GatewayRoute route,
                                                        long startTime) {
        int batchSize = batchArray.size();

        if (batchSize == 0) {
            return buildJsonRpcError(null, -32600, "Invalid Request: empty batch");
        }

        // Log batch info
        List<String> methodNames = new ArrayList<>();
        for (JsonNode node : batchArray) {
            if (node.has("method")) {
                methodNames.add(node.get("method").asText());
            }
        }
        log.info("JSON-RPC batch request: batchSize={}, methods={}", batchSize, methodNames);

        // Forward the entire batch to the upstream as-is
        try {
            byte[] batchBody = objectMapper.writeValueAsBytes(batchArray);
            return forwardToUpstream(route.getUpstreamUrl(), batchBody, startTime);
        } catch (IOException e) {
            log.error("Failed to serialize JSON-RPC batch: {}", e.getMessage());
            return buildJsonRpcError(null, -32603, "Internal error serializing batch");
        }
    }

    private ResponseEntity<byte[]> forwardToUpstream(String upstreamUrl, byte[] body, long startTime) {
        try {
            ResponseEntity<byte[]> response = restClient
                    .method(HttpMethod.POST)
                    .uri(URI.create(upstreamUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Don't throw — forward the upstream error as-is
                    })
                    .toEntity(byte[].class);

            long latencyMs = System.currentTimeMillis() - startTime;
            log.debug("JSON-RPC upstream response: status={}, latency={}ms",
                    response.getStatusCode(), latencyMs);

            return response;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("JSON-RPC upstream error: url={}, latency={}ms, error={}",
                    upstreamUrl, latencyMs, e.getMessage());
            return buildJsonRpcError(null, -32603, "Upstream error: " + e.getMessage());
        }
    }

    private ResponseEntity<byte[]> buildJsonRpcError(JsonNode id, int code, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (id != null && !id.isNull()) {
                response.set("id", id);
            } else {
                response.putNull("id");
            }
            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(response));
        } catch (IOException e) {
            // Fallback if JSON serialization fails
            return ResponseEntity.status(500)
                    .body(("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}").getBytes());
        }
    }
}
