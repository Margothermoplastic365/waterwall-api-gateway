package com.gateway.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.IdeCatalogEntry;
import com.gateway.management.dto.IdeLintRequest;
import com.gateway.management.dto.IdeLintResponse;
import com.gateway.management.dto.IdeCodeGenerationResponse;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backend API endpoints for IDE plugins (VS Code, JetBrains).
 * Provides lightweight catalog browsing, spec download, linting,
 * and code generation suitable for IDE consumption.
 */
@RestController
@RequestMapping("/v1/ide")
@RequiredArgsConstructor
public class IdeIntegrationController {

    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ObjectMapper objectMapper;

    /**
     * Lightweight API catalog for IDE sidebar.
     * Returns minimal data: id, name, version, status, protocol.
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<IdeCatalogEntry>> catalog(
            @RequestParam(required = false, defaultValue = "") String search) {

        List<ApiEntity> apis;
        if (search.isBlank()) {
            apis = apiRepository.findAll();
        } else {
            apis = apiRepository.findAll().stream()
                    .filter(a -> a.getName().toLowerCase().contains(search.toLowerCase())
                            || (a.getDescription() != null && a.getDescription().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        }

        List<IdeCatalogEntry> entries = apis.stream()
                .map(api -> IdeCatalogEntry.builder()
                        .id(api.getId())
                        .name(api.getName())
                        .version(api.getVersion())
                        .status(api.getStatus() != null ? api.getStatus().name() : "UNKNOWN")
                        .protocol(api.getProtocolType())
                        .category(api.getCategory())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(entries);
    }

    /**
     * Download OpenAPI spec for an API, suitable for IDE spec viewers.
     */
    @GetMapping("/spec/{apiId}")
    public ResponseEntity<Map<String, Object>> downloadSpec(@PathVariable UUID apiId) {
        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No spec found for API: " + apiId));

        return ResponseEntity.ok(Map.of(
                "apiId", apiId,
                "format", spec.getSpecFormat() != null ? spec.getSpecFormat() : "openapi3",
                "content", spec.getSpecContent() != null ? spec.getSpecContent() : ""
        ));
    }

    /**
     * Lint a spec sent directly from the IDE editor.
     * Accepts raw spec content and returns violations inline.
     */
    @PostMapping("/lint")
    public ResponseEntity<IdeLintResponse> lintSpec(@RequestBody IdeLintRequest request) {
        // Use inline linting logic similar to ApiLintingService
        List<Map<String, String>> violations = new ArrayList<>();
        int score = 100;

        try {
            JsonNode root = objectMapper.readTree(request.getSpecContent());

            // Check basic structure
            if (root.path("info").isMissingNode()) {
                violations.add(Map.of("severity", "ERROR", "rule", "info-required",
                        "path", "/info", "message", "OpenAPI spec must have an info object"));
                score -= 10;
            }
            if (root.path("paths").isMissingNode()) {
                violations.add(Map.of("severity", "ERROR", "rule", "paths-required",
                        "path", "/paths", "message", "OpenAPI spec must define paths"));
                score -= 10;
            }
            if (root.path("components").path("securitySchemes").isMissingNode()
                    || !root.path("components").path("securitySchemes").isObject()
                    || root.path("components").path("securitySchemes").isEmpty()) {
                violations.add(Map.of("severity", "WARNING", "rule", "security-scheme-defined",
                        "path", "/components/securitySchemes", "message", "API should define security schemes"));
                score -= 5;
            }
        } catch (Exception e) {
            violations.add(Map.of("severity", "ERROR", "rule", "valid-json",
                    "path", "/", "message", "Spec is not valid JSON/YAML: " + e.getMessage()));
            score = 0;
        }

        IdeLintResponse response = IdeLintResponse.builder()
                .score(Math.max(0, score))
                .violations(violations)
                .lintedAt(Instant.now())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Generate a client code snippet for a specific API in the requested language.
     */
    @PostMapping("/generate-code")
    public ResponseEntity<IdeCodeGenerationResponse> generateCode(
            @RequestParam UUID apiId,
            @RequestParam(defaultValue = "java") String language) {

        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No spec found for API: " + apiId));

        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        // Generate a simple code snippet based on language
        String snippet = generateSnippet(api.getName(), language, spec.getSpecContent());

        IdeCodeGenerationResponse response = IdeCodeGenerationResponse.builder()
                .apiId(apiId)
                .apiName(api.getName())
                .language(language)
                .snippet(snippet)
                .generatedAt(Instant.now())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * IDE connectivity health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "api-gateway-management",
                "timestamp", Instant.now().toString(),
                "version", "1.0.0"
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String generateSnippet(String apiName, String language, String specContent) {
        return switch (language.toLowerCase()) {
            case "java" -> generateJavaSnippet(apiName);
            case "python" -> generatePythonSnippet(apiName);
            case "javascript", "typescript" -> generateJsSnippet(apiName);
            case "curl" -> generateCurlSnippet(apiName);
            default -> "// Code generation for " + language + " is not yet supported.\n"
                    + "// Supported: java, python, javascript, typescript, curl";
        };
    }

    private String generateJavaSnippet(String apiName) {
        return """
                // Auto-generated Java client snippet for %s
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.net.URI;

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/v1"))
                    .header("Authorization", "Bearer YOUR_API_KEY")
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
                """.formatted(apiName);
    }

    private String generatePythonSnippet(String apiName) {
        return """
                # Auto-generated Python client snippet for %s
                import requests

                response = requests.get(
                    "http://localhost:8080/api/v1",
                    headers={
                        "Authorization": "Bearer YOUR_API_KEY",
                        "Content-Type": "application/json"
                    }
                )
                print(response.json())
                """.formatted(apiName);
    }

    private String generateJsSnippet(String apiName) {
        return """
                // Auto-generated JavaScript client snippet for %s
                const response = await fetch('http://localhost:8080/api/v1', {
                    method: 'GET',
                    headers: {
                        'Authorization': 'Bearer YOUR_API_KEY',
                        'Content-Type': 'application/json'
                    }
                });
                const data = await response.json();
                console.log(data);
                """.formatted(apiName);
    }

    private String generateCurlSnippet(String apiName) {
        return """
                # Auto-generated cURL snippet for %s
                curl -X GET "http://localhost:8080/api/v1" \\
                  -H "Authorization: Bearer YOUR_API_KEY" \\
                  -H "Content-Type: application/json"
                """.formatted(apiName);
    }
}
