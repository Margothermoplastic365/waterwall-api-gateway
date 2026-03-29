package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.ScoreResponse;
import com.gateway.management.entity.ApiChangelogEntity;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.entity.PolicyAttachmentEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.repository.ApiChangelogRepository;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import com.gateway.management.repository.PolicyAttachmentRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Scores an API from 0 to 100 across five weighted categories:
 * <ul>
 *   <li>Documentation (25 pts) - description, examples, error codes</li>
 *   <li>Security     (25 pts) - auth configured, rate-limit policies, no anonymous access</li>
 *   <li>Versioning   (15 pts) - has version, changelog entries</li>
 *   <li>Lifecycle    (15 pts) - published=full, created=partial, deprecated/retired=penalized</li>
 *   <li>Design       (20 pts) - tags, category, proper naming conventions</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiScoringService {

    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiChangelogRepository apiChangelogRepository;
    private final PolicyAttachmentRepository policyAttachmentRepository;
    private final RouteRepository routeRepository;
    private final ObjectMapper objectMapper;

    // ── Category weights (must sum to 100) ──────────────────────────────

    private static final int WEIGHT_DOCUMENTATION = 25;
    private static final int WEIGHT_SECURITY = 25;
    private static final int WEIGHT_VERSIONING = 15;
    private static final int WEIGHT_LIFECYCLE = 15;
    private static final int WEIGHT_DESIGN = 20;

    // ── Public API ──────────────────────────────────────────────────────

    @Transactional
    public ScoreResponse calculateScore(UUID apiId) {
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId).orElse(null);
        JsonNode root = parseSpec(spec != null ? spec.getSpecContent() : null);

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        List<String> recommendations = new ArrayList<>();

        int docRaw = scoreDocumentation(api, root, recommendations);
        breakdown.put("documentation", docRaw);

        int secRaw = scoreSecurity(api, apiId, root, recommendations);
        breakdown.put("security", secRaw);

        int verRaw = scoreVersioning(api, apiId, recommendations);
        breakdown.put("versioning", verRaw);

        int lifecycleRaw = scoreLifecycle(api, recommendations);
        breakdown.put("lifecycle", lifecycleRaw);

        int designRaw = scoreDesign(api, root, recommendations);
        breakdown.put("design", designRaw);

        int totalScore = (int) Math.round(
                docRaw       * (WEIGHT_DOCUMENTATION / 100.0) +
                secRaw       * (WEIGHT_SECURITY      / 100.0) +
                verRaw       * (WEIGHT_VERSIONING    / 100.0) +
                lifecycleRaw * (WEIGHT_LIFECYCLE     / 100.0) +
                designRaw    * (WEIGHT_DESIGN        / 100.0)
        );
        totalScore = Math.max(0, Math.min(100, totalScore));

        // Persist score on spec if available
        if (spec != null) {
            spec.setLintScore(totalScore);
            apiSpecRepository.save(spec);
        }

        log.info("Governance score for API {}: {} (doc={}, sec={}, ver={}, life={}, design={})",
                apiId, totalScore, docRaw, secRaw, verRaw, lifecycleRaw, designRaw);

        return ScoreResponse.builder()
                .apiId(apiId)
                .totalScore(totalScore)
                .breakdown(breakdown)
                .recommendations(recommendations)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ScoreResponse> getAllScores() {
        List<ApiEntity> apis = apiRepository.findAll();
        List<ScoreResponse> scores = new ArrayList<>();

        for (ApiEntity api : apis) {
            ApiSpecEntity spec = apiSpecRepository.findByApiId(api.getId()).orElse(null);
            int storedScore = spec != null && spec.getLintScore() != null ? spec.getLintScore() : 0;
            scores.add(ScoreResponse.builder()
                    .apiId(api.getId())
                    .totalScore(storedScore)
                    .breakdown(Collections.emptyMap())
                    .recommendations(Collections.emptyList())
                    .build());
        }

        scores.sort(Comparator.comparingInt(ScoreResponse::getTotalScore).reversed());
        return scores;
    }

    // ── 1. Documentation (0-100 raw) ────────────────────────────────────

    private int scoreDocumentation(ApiEntity api, JsonNode root, List<String> recommendations) {
        // Three sub-checks, each worth ~33 points of the raw 100
        int points = 0;

        // (a) description present on the API entity or spec info
        boolean hasDescription = api.getDescription() != null && !api.getDescription().isBlank();
        if (root != null) {
            String infoDesc = root.path("info").path("description").asText("");
            hasDescription = hasDescription || !infoDesc.isBlank();
        }
        if (hasDescription) {
            points += 34;
        } else {
            recommendations.add("Add a description to the API or spec info section");
        }

        // (b) examples present in spec
        if (root != null) {
            String specText = root.toString();
            if (specText.contains("\"example\"") || specText.contains("\"examples\"")) {
                points += 33;
            } else {
                recommendations.add("Add request/response examples to schemas and operations");
            }
        } else {
            recommendations.add("Upload an OpenAPI spec with examples for documentation scoring");
        }

        // (c) error codes (4xx/5xx/default) defined in responses
        if (root != null) {
            boolean hasErrorCodes = false;
            JsonNode paths = root.path("paths");
            if (paths.isObject()) {
                var pathFields = paths.fields();
                while (pathFields.hasNext() && !hasErrorCodes) {
                    var pathEntry = pathFields.next();
                    var methods = pathEntry.getValue().fields();
                    while (methods.hasNext() && !hasErrorCodes) {
                        var method = methods.next();
                        JsonNode responses = method.getValue().path("responses");
                        if (responses.isObject()) {
                            var codes = responses.fieldNames();
                            while (codes.hasNext()) {
                                String code = codes.next();
                                if (code.startsWith("4") || code.startsWith("5") || "default".equals(code)) {
                                    hasErrorCodes = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (hasErrorCodes) {
                points += 33;
            } else {
                recommendations.add("Define error response codes (4xx, 5xx) in operations");
            }
        } else {
            recommendations.add("Upload an OpenAPI spec to enable error-code scoring");
        }

        return Math.min(100, points);
    }

    // ── 2. Security (0-100 raw) ─────────────────────────────────────────

    private int scoreSecurity(ApiEntity api, UUID apiId, JsonNode root, List<String> recommendations) {
        int points = 0;

        // (a) Auth configured on the API entity (authMode not null/blank and not "NONE")
        boolean authConfigured = api.getAuthMode() != null
                && !api.getAuthMode().isBlank()
                && !"NONE".equalsIgnoreCase(api.getAuthMode());
        if (authConfigured) {
            points += 30;
        } else {
            recommendations.add("Configure an authentication mode (e.g., API_KEY, OAUTH2, JWT)");
        }

        // Also check spec-level security schemes
        if (root != null) {
            JsonNode securitySchemes = root.path("components").path("securitySchemes");
            if (securitySchemes.isObject() && !securitySchemes.isEmpty()) {
                points += 10;
            } else {
                recommendations.add("Define security schemes in the OpenAPI spec");
            }
        }

        // (b) Rate-limit policy attached
        List<PolicyAttachmentEntity> attachments = policyAttachmentRepository.findByApi_Id(apiId);
        boolean hasRateLimit = attachments.stream()
                .anyMatch(a -> {
                    String type = a.getPolicy() != null ? a.getPolicy().getType() : "";
                    return "RATE_LIMIT".equalsIgnoreCase(type)
                            || "THROTTLE".equalsIgnoreCase(type)
                            || "QUOTA".equalsIgnoreCase(type);
                });
        if (hasRateLimit) {
            points += 30;
        } else {
            recommendations.add("Attach a rate-limiting or throttle policy to this API");
        }

        // (c) No anonymous access
        boolean allowsAnonymous = Boolean.TRUE.equals(api.getAllowAnonymous());
        if (!allowsAnonymous) {
            points += 30;
        } else {
            recommendations.add("Disable anonymous access to improve security posture");
        }

        return Math.min(100, points);
    }

    // ── 3. Versioning (0-100 raw) ───────────────────────────────────────

    private int scoreVersioning(ApiEntity api, UUID apiId, List<String> recommendations) {
        int points = 0;

        // (a) Has a version string
        if (api.getVersion() != null && !api.getVersion().isBlank()) {
            points += 40;
            // Bonus for semantic versioning
            if (api.getVersion().matches("^\\d+\\.\\d+\\.\\d+.*$")) {
                points += 20;
            } else {
                recommendations.add("Use semantic versioning (e.g., 1.0.0) for clarity");
            }
        } else {
            recommendations.add("Set a version on the API");
        }

        // (b) Has changelog entries
        List<ApiChangelogEntity> changelogs = apiChangelogRepository.findByApiIdOrderByCreatedAtDesc(apiId);
        if (!changelogs.isEmpty()) {
            points += 30;
            if (changelogs.size() >= 3) {
                points += 10; // Bonus for consistent changelog maintenance
            }
        } else {
            recommendations.add("Maintain a changelog to document version changes");
        }

        return Math.min(100, points);
    }

    // ── 4. Lifecycle (0-100 raw) ────────────────────────────────────────

    private int scoreLifecycle(ApiEntity api, List<String> recommendations) {
        if (api.getStatus() == null) {
            recommendations.add("Set the API lifecycle status");
            return 0;
        }

        return switch (api.getStatus()) {
            case PUBLISHED -> {
                // Full marks
                yield 100;
            }
            case IN_REVIEW -> {
                recommendations.add("Complete the review process to publish the API");
                yield 70;
            }
            case DRAFT -> {
                recommendations.add("Move the API through review toward publication");
                yield 50;
            }
            case CREATED -> {
                recommendations.add("Develop the API further: add a spec, routes, and policies");
                yield 30;
            }
            case DEPRECATED -> {
                recommendations.add("Plan migration path for consumers of this deprecated API");
                yield 20;
            }
            case RETIRED -> {
                recommendations.add("This API is retired; consider removing it from the catalog");
                yield 0;
            }
        };
    }

    // ── 5. Design (0-100 raw) ───────────────────────────────────────────

    private int scoreDesign(ApiEntity api, JsonNode root, List<String> recommendations) {
        int points = 0;

        // (a) Has tags
        if (api.getTags() != null && !api.getTags().isEmpty()) {
            points += 30;
        } else {
            recommendations.add("Add tags to the API for discoverability");
        }

        // (b) Has category
        if (api.getCategory() != null && !api.getCategory().isBlank()) {
            points += 25;
        } else {
            recommendations.add("Assign a category to the API");
        }

        // (c) Proper naming: lowercase kebab-case or camelCase name, no special chars
        if (api.getName() != null && api.getName().matches("^[a-zA-Z][a-zA-Z0-9 \\-_]*$")) {
            points += 15;
        } else {
            recommendations.add("Use a clean alphanumeric name (letters, digits, hyphens, underscores)");
        }

        // (d) Path naming conventions in spec
        if (root != null) {
            JsonNode paths = root.path("paths");
            if (paths.isObject() && !paths.isEmpty()) {
                int totalPaths = 0;
                int conforming = 0;
                var pathNames = paths.fieldNames();
                while (pathNames.hasNext()) {
                    String pathName = pathNames.next();
                    totalPaths++;
                    String withoutParams = pathName.replaceAll("/\\{[^}]+}", "");
                    if (withoutParams.isEmpty()
                            || withoutParams.matches("^(/[a-z0-9]+(-[a-z0-9]+)*)*$")) {
                        conforming++;
                    }
                }
                if (totalPaths > 0) {
                    int pathScore = (conforming * 30) / totalPaths;
                    points += pathScore;
                    if (conforming < totalPaths) {
                        recommendations.add("Use lowercase kebab-case for all API paths");
                    }
                }
            } else {
                recommendations.add("Define API paths in the OpenAPI spec");
            }
        }

        return Math.min(100, points);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private JsonNode parseSpec(String specContent) {
        if (specContent == null || specContent.isBlank()) return null;
        try {
            return objectMapper.readTree(specContent);
        } catch (Exception e) {
            log.warn("Failed to parse spec: {}", e.getMessage());
            return null;
        }
    }
}
