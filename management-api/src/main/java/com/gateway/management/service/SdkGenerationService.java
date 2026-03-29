package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.management.dto.SdkResponse;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.repository.ApiSpecRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class SdkGenerationService {

    private final ApiSpecRepository apiSpecRepository;
    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;

    public SdkGenerationService(ApiSpecRepository apiSpecRepository,
                                 ObjectMapper objectMapper,
                                 @Value("${gateway.base-url:http://localhost:8080}") String gatewayBaseUrl) {
        this.apiSpecRepository = apiSpecRepository;
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    private static final List<String> SUPPORTED_LANGUAGES = List.of("curl", "postman", "javascript", "python", "java", "csharp", "php");

    public List<String> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public SdkResponse generateSdk(UUID apiId, String language) {
        if (!SUPPORTED_LANGUAGES.contains(language.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported language: " + language
                    + ". Supported: " + SUPPORTED_LANGUAGES);
        }

        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No API spec found for apiId: " + apiId));

        log.info("Generating SDK for apiId={} language={}", apiId, language);

        return SdkResponse.builder()
                .apiId(apiId)
                .language(language.toLowerCase())
                .downloadUrl("/v1/sdks/download/" + apiId + "?language=" + language.toLowerCase())
                .generatedAt(Instant.now())
                .build();
    }

    public byte[] downloadSdk(UUID apiId, String language) {
        ApiSpecEntity specEntity = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No API spec found for apiId: " + apiId));

        String specContent = specEntity.getSpecContent();

        try {
            return switch (language.toLowerCase()) {
                case "curl" -> generateCurlSdk(specContent, apiId);
                case "postman" -> generatePostmanSdk(specContent, apiId);
                case "javascript" -> generateJavaScriptSdk(specContent, apiId);
                case "python" -> generatePythonSdk(specContent, apiId);
                case "java" -> generateJavaSdk(specContent, apiId);
                case "csharp" -> generateCSharpSdk(specContent, apiId);
                case "php" -> generatePhpSdk(specContent, apiId);
                default -> throw new IllegalArgumentException("Unsupported language: " + language);
            };
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate SDK: " + e.getMessage(), e);
        }
    }

    // ── cURL Generation ──────────────────────────────────────────────────

    private byte[] generateCurlSdk(String specContent, UUID apiId) throws IOException {
        JsonNode spec = objectMapper.readTree(specContent);
        StringBuilder curlCommands = new StringBuilder();
        curlCommands.append("#!/bin/bash\n");
        curlCommands.append("# Auto-generated cURL commands for API: ").append(getTitle(spec)).append("\n");
        curlCommands.append("# Generated at: ").append(Instant.now()).append("\n\n");
        curlCommands.append("BASE_URL=\"${BASE_URL:-" + gatewayBaseUrl + "}\"\n");
        curlCommands.append("API_KEY=\"${API_KEY:-your-api-key}\"\n\n");

        String basePath = getBasePath(spec);

        JsonNode paths = spec.get("paths");
        if (paths != null) {
            for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> pathEntry = it.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                for (Iterator<Map.Entry<String, JsonNode>> methodIt = methods.fields(); methodIt.hasNext(); ) {
                    Map.Entry<String, JsonNode> methodEntry = methodIt.next();
                    String method = methodEntry.getKey().toUpperCase();
                    if (List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                        JsonNode operation = methodEntry.getValue();
                        String summary = operation.has("summary") ? operation.get("summary").asText() : path;

                        curlCommands.append("# ").append(summary).append("\n");
                        curlCommands.append("curl -X ").append(method);
                        curlCommands.append(" \"${BASE_URL}").append(basePath).append(path).append("\"");
                        curlCommands.append(" \\\n  -H \"Authorization: Bearer ${API_KEY}\"");
                        curlCommands.append(" \\\n  -H \"Content-Type: application/json\"");

                        if (List.of("POST", "PUT", "PATCH").contains(method)) {
                            curlCommands.append(" \\\n  -d '{}'");
                        }
                        curlCommands.append("\n\n");
                    }
                }
            }
        }

        return zipContent("curl-commands.sh", curlCommands.toString());
    }

    // ── Postman Collection Generation ────────────────────────────────────

    private byte[] generatePostmanSdk(String specContent, UUID apiId) throws IOException {
        JsonNode spec = objectMapper.readTree(specContent);
        ObjectNode collection = objectMapper.createObjectNode();

        ObjectNode info = objectMapper.createObjectNode();
        info.put("name", getTitle(spec) + " API Collection");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        info.put("description", "Auto-generated Postman collection");
        collection.set("info", info);

        ArrayNode items = objectMapper.createArrayNode();
        String basePath = getBasePath(spec);

        JsonNode paths = spec.get("paths");
        if (paths != null) {
            for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> pathEntry = it.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                for (Iterator<Map.Entry<String, JsonNode>> methodIt = methods.fields(); methodIt.hasNext(); ) {
                    Map.Entry<String, JsonNode> methodEntry = methodIt.next();
                    String method = methodEntry.getKey().toUpperCase();
                    if (List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                        JsonNode operation = methodEntry.getValue();
                        String summary = operation.has("summary")
                                ? operation.get("summary").asText()
                                : method + " " + path;

                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("name", summary);

                        ObjectNode request = objectMapper.createObjectNode();
                        request.put("method", method);

                        ObjectNode url = objectMapper.createObjectNode();
                        url.put("raw", "{{baseUrl}}" + basePath + path);
                        url.put("host", "{{baseUrl}}");
                        url.put("path", basePath + path);
                        request.set("url", url);

                        ArrayNode headers = objectMapper.createArrayNode();
                        ObjectNode authHeader = objectMapper.createObjectNode();
                        authHeader.put("key", "Authorization");
                        authHeader.put("value", "Bearer {{apiKey}}");
                        headers.add(authHeader);
                        ObjectNode contentTypeHeader = objectMapper.createObjectNode();
                        contentTypeHeader.put("key", "Content-Type");
                        contentTypeHeader.put("value", "application/json");
                        headers.add(contentTypeHeader);
                        request.set("header", headers);

                        item.set("request", request);
                        items.add(item);
                    }
                }
            }
        }

        collection.set("item", items);

        // Add variables
        ArrayNode variables = objectMapper.createArrayNode();
        ObjectNode baseUrlVar = objectMapper.createObjectNode();
        baseUrlVar.put("key", "baseUrl");
        baseUrlVar.put("value", gatewayBaseUrl);
        variables.add(baseUrlVar);
        ObjectNode apiKeyVar = objectMapper.createObjectNode();
        apiKeyVar.put("key", "apiKey");
        apiKeyVar.put("value", "your-api-key");
        variables.add(apiKeyVar);
        collection.set("variable", variables);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(collection);
        return zipContent("postman-collection.json", json);
    }

    // ── JavaScript SDK Generation ────────────────────────────────────────

    private byte[] generateJavaScriptSdk(String specContent, UUID apiId) throws IOException {
        JsonNode spec = objectMapper.readTree(specContent);
        StringBuilder js = new StringBuilder();
        String title = getTitle(spec);
        String className = toPascalCase(title) + "Client";

        js.append("/**\n * Auto-generated JavaScript SDK for ").append(title).append("\n");
        js.append(" * Generated at: ").append(Instant.now()).append("\n */\n\n");
        js.append("class ").append(className).append(" {\n");
        js.append("  constructor(baseUrl = '" + gatewayBaseUrl + "', apiKey = '') {\n");
        js.append("    this.baseUrl = baseUrl;\n");
        js.append("    this.apiKey = apiKey;\n");
        js.append("  }\n\n");
        js.append("  async _request(method, path, body = null) {\n");
        js.append("    const headers = {\n");
        js.append("      'Content-Type': 'application/json',\n");
        js.append("      'Authorization': `Bearer ${this.apiKey}`\n");
        js.append("    };\n");
        js.append("    const options = { method, headers };\n");
        js.append("    if (body) options.body = JSON.stringify(body);\n");
        js.append("    const response = await fetch(`${this.baseUrl}${path}`, options);\n");
        js.append("    if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);\n");
        js.append("    return response.json();\n");
        js.append("  }\n\n");

        String basePath = getBasePath(spec);
        JsonNode paths = spec.get("paths");
        if (paths != null) {
            for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> pathEntry = it.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                for (Iterator<Map.Entry<String, JsonNode>> methodIt = methods.fields(); methodIt.hasNext(); ) {
                    Map.Entry<String, JsonNode> methodEntry = methodIt.next();
                    String method = methodEntry.getKey().toUpperCase();
                    if (List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                        JsonNode operation = methodEntry.getValue();
                        String operationId = operation.has("operationId")
                                ? operation.get("operationId").asText()
                                : toCamelCase(method + " " + path);

                        boolean hasBody = List.of("POST", "PUT", "PATCH").contains(method);
                        String params = hasBody ? "body = {}" : "";

                        js.append("  async ").append(operationId).append("(").append(params).append(") {\n");
                        js.append("    return this._request('").append(method).append("', '");
                        js.append(basePath).append(path).append("'");
                        if (hasBody) js.append(", body");
                        js.append(");\n");
                        js.append("  }\n\n");
                    }
                }
            }
        }

        js.append("}\n\n");
        js.append("module.exports = ").append(className).append(";\n");

        return zipContent("sdk.js", js.toString());
    }

    // ── Python SDK Generation ────────────────────────────────────────────

    private byte[] generatePythonSdk(String specContent, UUID apiId) throws IOException {
        JsonNode spec = objectMapper.readTree(specContent);
        StringBuilder py = new StringBuilder();
        String title = getTitle(spec);
        String className = toPascalCase(title) + "Client";

        py.append("\"\"\"Auto-generated Python SDK for ").append(title).append("\n");
        py.append("Generated at: ").append(Instant.now()).append("\n\"\"\"\n\n");
        py.append("import requests\n\n\n");
        py.append("class ").append(className).append(":\n");
        py.append("    def __init__(self, base_url='" + gatewayBaseUrl + "', api_key=''):\n");
        py.append("        self.base_url = base_url\n");
        py.append("        self.api_key = api_key\n");
        py.append("        self.session = requests.Session()\n");
        py.append("        self.session.headers.update({\n");
        py.append("            'Content-Type': 'application/json',\n");
        py.append("            'Authorization': f'Bearer {self.api_key}'\n");
        py.append("        })\n\n");

        String basePath = getBasePath(spec);
        JsonNode paths = spec.get("paths");
        if (paths != null) {
            for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> pathEntry = it.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                for (Iterator<Map.Entry<String, JsonNode>> methodIt = methods.fields(); methodIt.hasNext(); ) {
                    Map.Entry<String, JsonNode> methodEntry = methodIt.next();
                    String method = methodEntry.getKey().toUpperCase();
                    if (List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                        JsonNode operation = methodEntry.getValue();
                        String operationId = operation.has("operationId")
                                ? toSnakeCase(operation.get("operationId").asText())
                                : toSnakeCase(method + "_" + path.replace("/", "_"));

                        boolean hasBody = List.of("POST", "PUT", "PATCH").contains(method);
                        String params = hasBody ? "self, body=None" : "self";

                        py.append("    def ").append(operationId).append("(").append(params).append("):\n");
                        String summary = operation.has("summary") ? operation.get("summary").asText() : operationId;
                        py.append("        \"\"\"").append(summary).append("\"\"\"\n");
                        py.append("        url = f'{self.base_url}").append(basePath).append(path).append("'\n");
                        if (hasBody) {
                            py.append("        return self.session.").append(method.toLowerCase());
                            py.append("(url, json=body).json()\n\n");
                        } else {
                            py.append("        return self.session.").append(method.toLowerCase());
                            py.append("(url).json()\n\n");
                        }
                    }
                }
            }
        }

        return zipContent("sdk.py", py.toString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String getTitle(JsonNode spec) {
        JsonNode info = spec.get("info");
        if (info != null && info.has("title")) {
            return info.get("title").asText();
        }
        return "API";
    }

    private String getBasePath(JsonNode spec) {
        // OpenAPI 3: servers[0].url
        JsonNode servers = spec.get("servers");
        if (servers != null && servers.isArray() && !servers.isEmpty()) {
            String serverUrl = servers.get(0).get("url").asText();
            if (serverUrl.startsWith("http")) {
                try {
                    java.net.URI uri = java.net.URI.create(serverUrl);
                    return uri.getPath() != null ? uri.getPath() : "";
                } catch (Exception e) {
                    return "";
                }
            }
            return serverUrl;
        }
        // Swagger 2: basePath
        if (spec.has("basePath")) {
            return spec.get("basePath").asText();
        }
        return "";
    }

    private byte[] zipContent(String filename, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(filename);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String toPascalCase(String input) {
        if (input == null || input.isBlank()) return "Api";
        StringBuilder result = new StringBuilder();
        for (String word : input.split("[\\s\\-_/]+")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    private String toCamelCase(String input) {
        String pascal = toPascalCase(input);
        if (pascal.isEmpty()) return "api";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String toSnakeCase(String input) {
        if (input == null || input.isBlank()) return "api";
        return input.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[\\s\\-/]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
    }

    // ── Java SDK ────────────────────────────────────────────────────────

    private byte[] generateJavaSdk(String specContent, UUID apiId) throws IOException {
        JsonNode root = objectMapper.readTree(specContent);
        String title = root.path("info").path("title").asText("API");
        String className = title.replaceAll("[^a-zA-Z0-9]", "") + "Client";
        String baseUrl = root.path("servers").path(0).path("url").asText(gatewayBaseUrl);

        StringBuilder sb = new StringBuilder();
        sb.append("import java.net.URI;\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.http.HttpResponse.BodyHandlers;\n\n");
        sb.append("/**\n * Auto-generated Java client for ").append(title).append("\n */\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    private final HttpClient client = HttpClient.newHttpClient();\n");
        sb.append("    private final String baseUrl;\n");
        sb.append("    private final String apiKey;\n\n");
        sb.append("    public ").append(className).append("(String apiKey) {\n");
        sb.append("        this(\"").append(baseUrl).append("\", apiKey);\n");
        sb.append("    }\n\n");
        sb.append("    public ").append(className).append("(String baseUrl, String apiKey) {\n");
        sb.append("        this.baseUrl = baseUrl;\n");
        sb.append("        this.apiKey = apiKey;\n");
        sb.append("    }\n\n");

        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            paths.fields().forEachRemaining(pathEntry -> {
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) return;
                    String path = pathEntry.getKey();
                    String methodName = toSnakeCase(method.toLowerCase() + "_" + path);
                    String summary = methodEntry.getValue().path("summary").asText("");

                    sb.append("    /** ").append(summary).append(" */\n");
                    sb.append("    public String ").append(methodName).append("(String body) throws Exception {\n");
                    sb.append("        HttpRequest.Builder builder = HttpRequest.newBuilder()\n");
                    sb.append("            .uri(URI.create(baseUrl + \"").append(path).append("\"))\n");
                    sb.append("            .header(\"X-API-Key\", apiKey)\n");
                    sb.append("            .header(\"Accept\", \"application/json\");\n");
                    if ("GET".equals(method) || "DELETE".equals(method)) {
                        sb.append("        builder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody());\n");
                    } else {
                        sb.append("        builder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(body != null ? body : \"\"));\n");
                        sb.append("        builder.header(\"Content-Type\", \"application/json\");\n");
                    }
                    sb.append("        HttpResponse<String> response = client.send(builder.build(), BodyHandlers.ofString());\n");
                    sb.append("        return response.body();\n");
                    sb.append("    }\n\n");
                });
            });
        }

        sb.append("}\n");

        return zipContent(className + ".java", sb.toString());
    }

    // ── C# SDK ──────────────────────────────────────────────────────────

    private byte[] generateCSharpSdk(String specContent, UUID apiId) throws IOException {
        JsonNode root = objectMapper.readTree(specContent);
        String title = root.path("info").path("title").asText("API");
        String className = title.replaceAll("[^a-zA-Z0-9]", "") + "Client";
        String baseUrl = root.path("servers").path(0).path("url").asText(gatewayBaseUrl);

        StringBuilder sb = new StringBuilder();
        sb.append("using System;\nusing System.Net.Http;\nusing System.Net.Http.Headers;\nusing System.Text;\nusing System.Threading.Tasks;\n\n");
        sb.append("/// <summary>\n/// Auto-generated C# client for ").append(title).append("\n/// </summary>\n");
        sb.append("public class ").append(className).append("\n{\n");
        sb.append("    private readonly HttpClient _client;\n");
        sb.append("    private readonly string _baseUrl;\n\n");
        sb.append("    public ").append(className).append("(string apiKey, string baseUrl = \"").append(baseUrl).append("\")\n");
        sb.append("    {\n");
        sb.append("        _baseUrl = baseUrl;\n");
        sb.append("        _client = new HttpClient();\n");
        sb.append("        _client.DefaultRequestHeaders.Add(\"X-API-Key\", apiKey);\n");
        sb.append("        _client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue(\"application/json\"));\n");
        sb.append("    }\n\n");

        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            paths.fields().forEachRemaining(pathEntry -> {
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) return;
                    String path = pathEntry.getKey();
                    String methodName = toPascalCase(toSnakeCase(method.toLowerCase() + "_" + path));
                    String summary = methodEntry.getValue().path("summary").asText("");

                    sb.append("    /// <summary>").append(summary).append("</summary>\n");
                    sb.append("    public async Task<string> ").append(methodName).append("Async(string body = null)\n");
                    sb.append("    {\n");
                    if ("GET".equals(method)) {
                        sb.append("        var response = await _client.GetAsync($\"{_baseUrl}").append(path).append("\");\n");
                    } else if ("DELETE".equals(method)) {
                        sb.append("        var response = await _client.DeleteAsync($\"{_baseUrl}").append(path).append("\");\n");
                    } else {
                        sb.append("        var content = new StringContent(body ?? \"\", Encoding.UTF8, \"application/json\");\n");
                        sb.append("        var response = await _client.").append(method.equals("POST") ? "PostAsync" : "PutAsync")
                          .append("($\"{_baseUrl}").append(path).append("\", content);\n");
                    }
                    sb.append("        response.EnsureSuccessStatusCode();\n");
                    sb.append("        return await response.Content.ReadAsStringAsync();\n");
                    sb.append("    }\n\n");
                });
            });
        }

        sb.append("}\n");

        return zipContent(className + ".cs", sb.toString());
    }

    // ── PHP SDK ─────────────────────────────────────────────────────────

    private byte[] generatePhpSdk(String specContent, UUID apiId) throws IOException {
        JsonNode root = objectMapper.readTree(specContent);
        String title = root.path("info").path("title").asText("API");
        String className = title.replaceAll("[^a-zA-Z0-9]", "") + "Client";
        String baseUrl = root.path("servers").path(0).path("url").asText(gatewayBaseUrl);

        StringBuilder sb = new StringBuilder();
        sb.append("<?php\n\n");
        sb.append("/**\n * Auto-generated PHP client for ").append(title).append("\n */\n");
        sb.append("class ").append(className).append("\n{\n");
        sb.append("    private string $baseUrl;\n");
        sb.append("    private string $apiKey;\n\n");
        sb.append("    public function __construct(string $apiKey, string $baseUrl = '").append(baseUrl).append("')\n");
        sb.append("    {\n");
        sb.append("        $this->baseUrl = $baseUrl;\n");
        sb.append("        $this->apiKey = $apiKey;\n");
        sb.append("    }\n\n");
        sb.append("    private function request(string $method, string $path, ?string $body = null): string\n");
        sb.append("    {\n");
        sb.append("        $ch = curl_init($this->baseUrl . $path);\n");
        sb.append("        curl_setopt_array($ch, [\n");
        sb.append("            CURLOPT_RETURNTRANSFER => true,\n");
        sb.append("            CURLOPT_CUSTOMREQUEST => $method,\n");
        sb.append("            CURLOPT_HTTPHEADER => [\n");
        sb.append("                'X-API-Key: ' . $this->apiKey,\n");
        sb.append("                'Accept: application/json',\n");
        sb.append("                'Content-Type: application/json',\n");
        sb.append("            ],\n");
        sb.append("        ]);\n");
        sb.append("        if ($body !== null) {\n");
        sb.append("            curl_setopt($ch, CURLOPT_POSTFIELDS, $body);\n");
        sb.append("        }\n");
        sb.append("        $response = curl_exec($ch);\n");
        sb.append("        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);\n");
        sb.append("        curl_close($ch);\n");
        sb.append("        if ($httpCode >= 400) {\n");
        sb.append("            throw new \\RuntimeException(\"HTTP $httpCode: $response\");\n");
        sb.append("        }\n");
        sb.append("        return $response;\n");
        sb.append("    }\n\n");

        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            paths.fields().forEachRemaining(pathEntry -> {
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) return;
                    String path = pathEntry.getKey();
                    String methodName = toSnakeCase(method.toLowerCase() + "_" + path);
                    String summary = methodEntry.getValue().path("summary").asText("");

                    sb.append("    /** ").append(summary).append(" */\n");
                    sb.append("    public function ").append(methodName).append("(?string $body = null): string\n");
                    sb.append("    {\n");
                    sb.append("        return $this->request('").append(method).append("', '").append(path).append("', $body);\n");
                    sb.append("    }\n\n");
                });
            });
        }

        sb.append("}\n");

        return zipContent(className + ".php", sb.toString());
    }

}
