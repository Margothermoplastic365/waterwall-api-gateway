package com.gateway.management.controller;

import com.gateway.management.dto.*;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.entity.ApiTemplateEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.entity.SharedFlowEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import com.gateway.management.repository.RouteRepository;
import com.gateway.management.repository.SharedFlowRepository;
import com.gateway.management.service.ApiHubService;
import com.gateway.management.service.ApiLintingService;
import com.gateway.management.service.ApiScoringService;
import com.gateway.management.service.GovernanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final ApiLintingService apiLintingService;
    private final ApiScoringService apiScoringService;
    private final GovernanceService governanceService;
    private final ApiHubService apiHubService;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiRepository apiRepository;
    private final RouteRepository routeRepository;
    private final SharedFlowRepository sharedFlowRepository;

    // ── Governance report endpoints ─────────────────────────────────────

    @GetMapping("/report/{apiId}")
    public ResponseEntity<GovernanceReportResponse> getGovernanceReport(@PathVariable UUID apiId) {
        GovernanceReportResponse report = governanceService.getApiGovernanceReport(apiId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/overview")
    public ResponseEntity<GovernanceOverviewResponse> getGovernanceOverview() {
        GovernanceOverviewResponse overview = governanceService.getGovernanceOverview();
        return ResponseEntity.ok(overview);
    }

    // ── Linting endpoints ────────────────────────────────────────────────

    @PostMapping("/lint/{apiId}")
    public ResponseEntity<LintReportResponse> lintApiSpec(@PathVariable UUID apiId) {
        LintReportResponse report = apiLintingService.lintApiSpec(apiId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/lint/{apiId}/report")
    public ResponseEntity<LintReportResponse> getLintReport(@PathVariable UUID apiId) {
        LintReportResponse report = apiLintingService.getLatestReport(apiId);
        return ResponseEntity.ok(report);
    }

    // ── Spec management endpoints ────────────────────────────────────────

    @PostMapping("/specs/{apiId}")
    public ResponseEntity<ApiSpecEntity> uploadSpec(@PathVariable UUID apiId,
                                                     @RequestBody UploadSpecRequest request) {
        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElse(ApiSpecEntity.builder().apiId(apiId).build());

        spec.setSpecContent(request.getSpecContent());
        if (request.getSpecFormat() != null && !request.getSpecFormat().isBlank()) {
            spec.setSpecFormat(request.getSpecFormat());
        }

        ApiSpecEntity saved = apiSpecRepository.save(spec);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/specs/{apiId}")
    public ResponseEntity<ApiSpecEntity> getSpec(@PathVariable UUID apiId) {
        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No spec found for API: " + apiId));
        return ResponseEntity.ok(spec);
    }

    /**
     * Returns the OpenAPI spec for an API. If a manually uploaded spec exists, returns that.
     * Otherwise auto-generates a basic OpenAPI 3.0 spec from the API's routes.
     */
    @GetMapping(value = "/specs/{apiId}/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOpenApiSpec(@PathVariable UUID apiId) {
        // Try uploaded spec first — override servers with gateway URL
        var uploaded = apiSpecRepository.findByApiId(apiId);
        if (uploaded.isPresent() && uploaded.get().getSpecContent() != null && !uploaded.get().getSpecContent().isBlank()) {
            String specContent = uploaded.get().getSpecContent();
            String gw = System.getenv("GATEWAY_URL") != null
                    ? System.getenv("GATEWAY_URL") : "http://localhost:8080";
            // Replace servers array with gateway URL
            specContent = specContent.replaceFirst(
                    "\"servers\"\\s*:\\s*\\[.*?\\]",
                    "\"servers\": [{\"url\": \"" + gw + "\", \"description\": \"Gateway\"}]"
            );
            return ResponseEntity.ok(specContent);
        }

        // Auto-generate from routes
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        List<RouteEntity> routes = routeRepository.findByApiId(apiId);

        StringBuilder spec = new StringBuilder();
        spec.append("{\n");
        spec.append("  \"openapi\": \"3.0.3\",\n");
        spec.append("  \"info\": {\n");
        spec.append("    \"title\": \"").append(escapeJson(api.getName())).append("\",\n");
        spec.append("    \"description\": \"").append(escapeJson(api.getDescription() != null ? api.getDescription() : "")).append("\",\n");
        spec.append("    \"version\": \"").append(escapeJson(api.getVersion() != null ? api.getVersion() : "1.0.0")).append("\"\n");
        spec.append("  },\n");
        String gatewayUrl = System.getenv("GATEWAY_URL") != null
                ? System.getenv("GATEWAY_URL") : "http://localhost:8080";
        spec.append("  \"servers\": [\n");
        spec.append("    { \"url\": \"").append(gatewayUrl).append("\", \"description\": \"Gateway\" }\n");
        spec.append("  ],\n");

        // Security schemes
        spec.append("  \"components\": {\n");
        spec.append("    \"securitySchemes\": {\n");
        spec.append("      \"ApiKeyAuth\": { \"type\": \"apiKey\", \"in\": \"header\", \"name\": \"X-API-Key\" },\n");
        spec.append("      \"BearerAuth\": { \"type\": \"http\", \"scheme\": \"bearer\", \"bearerFormat\": \"JWT\" }\n");
        spec.append("    }\n");
        spec.append("  },\n");

        // Paths
        spec.append("  \"paths\": {\n");
        var pathGroups = new java.util.LinkedHashMap<String, java.util.List<RouteEntity>>();
        for (RouteEntity route : routes) {
            pathGroups.computeIfAbsent(route.getPath(), k -> new java.util.ArrayList<>()).add(route);
        }

        boolean firstPath = true;
        for (var entry : pathGroups.entrySet()) {
            if (!firstPath) spec.append(",\n");
            firstPath = false;
            spec.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");

            boolean firstMethod = true;
            for (RouteEntity route : entry.getValue()) {
                if (!firstMethod) spec.append(",\n");
                firstMethod = false;
                String method = route.getMethod() != null ? route.getMethod().toLowerCase() : "get";
                spec.append("      \"").append(method).append("\": {\n");
                spec.append("        \"summary\": \"").append(method.toUpperCase()).append(" ").append(escapeJson(entry.getKey())).append("\",\n");
                spec.append("        \"tags\": [\"").append(escapeJson(api.getName())).append("\"],\n");

                // Security
                List<String> authTypes = route.getAuthTypes();
                if (authTypes != null && !authTypes.isEmpty()) {
                    spec.append("        \"security\": [");
                    boolean firstAuth = true;
                    for (String auth : authTypes) {
                        if (!firstAuth) spec.append(", ");
                        firstAuth = false;
                        if ("API_KEY".equals(auth)) spec.append("{ \"ApiKeyAuth\": [] }");
                        else if ("OAUTH2".equals(auth) || "JWT".equals(auth)) spec.append("{ \"BearerAuth\": [] }");
                    }
                    spec.append("],\n");
                }

                spec.append("        \"responses\": {\n");
                spec.append("          \"200\": { \"description\": \"Success\" },\n");
                spec.append("          \"401\": { \"description\": \"Unauthorized\" },\n");
                spec.append("          \"500\": { \"description\": \"Internal Server Error\" }\n");
                spec.append("        }\n");
                spec.append("      }");
            }
            spec.append("\n    }");
        }
        spec.append("\n  }\n");
        spec.append("}");

        return ResponseEntity.ok(spec.toString());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }

    // ── Scoring endpoints ────────────────────────────────────────────────

    @GetMapping("/score/{apiId}")
    public ResponseEntity<ScoreResponse> getScore(@PathVariable UUID apiId) {
        ScoreResponse score = apiScoringService.calculateScore(apiId);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/scores")
    public ResponseEntity<List<ScoreResponse>> getAllScores() {
        List<ScoreResponse> scores = apiScoringService.getAllScores();
        return ResponseEntity.ok(scores);
    }

    // ── API Hub endpoints ────────────────────────────────────────────────

    @GetMapping("/hub")
    public ResponseEntity<List<ApiHubEntry>> getApiHub() {
        List<ApiHubEntry> hub = apiHubService.getApiHub();
        return ResponseEntity.ok(hub);
    }

    // ── Template endpoints ───────────────────────────────────────────────

    @GetMapping("/templates")
    public ResponseEntity<List<ApiTemplateEntity>> listTemplates() {
        List<ApiTemplateEntity> templates = apiHubService.listTemplates();
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/templates/apply")
    public ResponseEntity<ApiSpecEntity> applyTemplate(@RequestBody ApplyTemplateRequest request) {
        ApiSpecEntity spec = apiHubService.createFromTemplate(request.getTemplateId(), request.getApiName());
        return ResponseEntity.status(HttpStatus.CREATED).body(spec);
    }

    // ── Shared flows endpoints ───────────────────────────────────────────

    @GetMapping("/shared-flows")
    public ResponseEntity<List<SharedFlowEntity>> listSharedFlows() {
        List<SharedFlowEntity> flows = sharedFlowRepository.findAll();
        return ResponseEntity.ok(flows);
    }

    @PostMapping("/shared-flows")
    public ResponseEntity<SharedFlowEntity> createSharedFlow(@RequestBody CreateSharedFlowRequest request) {
        SharedFlowEntity flow = SharedFlowEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .policyChain(request.getPolicyChain())
                .scope(request.getScope())
                .build();
        SharedFlowEntity saved = sharedFlowRepository.save(flow);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
