package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.LintReportResponse;
import com.gateway.management.dto.LintViolation;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lints an API spec and returns a list of violations.
 *
 * <p>Rules enforced:</p>
 * <ol>
 *   <li>Paths must start with /</li>
 *   <li>All operations must have descriptions (or summaries)</li>
 *   <li>All operations must define responses</li>
 *   <li>Path parameters must be documented</li>
 *   <li>Contact info must be present</li>
 *   <li>License info must be present</li>
 *   <li>Paths should be lowercase kebab-case</li>
 *   <li>Operations should have operationId</li>
 *   <li>Security schemes must be defined</li>
 *   <li>Error responses (4xx/5xx) should be present</li>
 * </ol>
 *
 * Each violation carries: {rule, severity, path, message}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLintingService {

    private static final Pattern KEBAB_CASE =
            Pattern.compile("^/[a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)*(/(\\{[a-zA-Z0-9_]+}))*$");

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    private static final int ERROR_WEIGHT = 10;
    private static final int WARNING_WEIGHT = 5;
    private static final int INFO_WEIGHT = 1;

    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ObjectMapper objectMapper;

    // ── Public API ──────────────────────────────────────────────────────

    @Transactional
    public LintReportResponse lintApiSpec(UUID apiId) {
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No spec found for API: " + apiId));

        List<LintViolation> violations = runAllRules(spec.getSpecContent());

        int score = calculateLintScore(violations);

        spec.setLintScore(score);
        spec.setLastLintedAt(Instant.now());
        apiSpecRepository.save(spec);

        log.info("Linted API spec: apiId={}, score={}, violations={}", apiId, score, violations.size());

        return LintReportResponse.builder()
                .apiId(apiId)
                .apiName(api.getName())
                .score(score)
                .violations(violations)
                .lintedAt(spec.getLastLintedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public LintReportResponse getLatestReport(UUID apiId) {
        ApiEntity api = apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        ApiSpecEntity spec = apiSpecRepository.findByApiId(apiId)
                .orElseThrow(() -> new EntityNotFoundException("No spec found for API: " + apiId));

        List<LintViolation> violations = runAllRules(spec.getSpecContent());

        return LintReportResponse.builder()
                .apiId(apiId)
                .apiName(api.getName())
                .score(spec.getLintScore() != null ? spec.getLintScore() : 0)
                .violations(violations)
                .lintedAt(spec.getLastLintedAt())
                .build();
    }

    /**
     * Run lint rules on raw spec content without requiring an API entity.
     * Used by GovernanceService for on-the-fly linting.
     */
    public List<LintViolation> lintSpecContent(String specContent) {
        return runAllRules(specContent);
    }

    // ── Rule orchestration ──────────────────────────────────────────────

    private List<LintViolation> runAllRules(String specContent) {
        List<LintViolation> violations = new ArrayList<>();

        if (specContent == null || specContent.isBlank()) {
            violations.add(violation("ERROR", "spec-not-empty", "/", "API spec is empty"));
            return violations;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(specContent);
        } catch (Exception e) {
            log.warn("Failed to parse spec for linting: {}", e.getMessage());
            violations.add(violation("ERROR", "spec-valid-json", "/",
                    "Spec is not valid JSON/YAML: " + e.getMessage()));
            return violations;
        }

        checkPathsStartWithSlash(root, violations);
        checkOperationDescriptions(root, violations);
        checkOperationResponses(root, violations);
        checkPathParamsDocumented(root, violations);
        checkContactInfo(root, violations);
        checkLicenseInfo(root, violations);
        checkKebabCasePaths(root, violations);
        checkOperationIds(root, violations);
        checkSecuritySchemes(root, violations);
        checkErrorResponses(root, violations);

        return violations;
    }

    // ── Rule 1: Paths start with / ──────────────────────────────────────

    private void checkPathsStartWithSlash(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            violations.add(violation("ERROR", "paths-defined", "/paths",
                    "No paths object defined in spec"));
            return;
        }

        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String pathName = pathNames.next();
            if (!pathName.startsWith("/")) {
                violations.add(violation("ERROR", "path-starts-with-slash", pathName,
                        "Path must start with '/' but found: " + pathName));
            }
        }
    }

    // ── Rule 2: Operations have descriptions ────────────────────────────

    private void checkOperationDescriptions(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        forEachOperation(paths, (pathKey, method, operation) -> {
            String desc = operation.path("description").asText("");
            String summary = operation.path("summary").asText("");
            if (desc.isBlank() && summary.isBlank()) {
                violations.add(violation("WARNING", "operation-description",
                        pathKey + "." + method,
                        "Operation must have a description or summary"));
            }
        });
    }

    // ── Rule 3: Operations have responses ───────────────────────────────

    private void checkOperationResponses(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        forEachOperation(paths, (pathKey, method, operation) -> {
            JsonNode responses = operation.path("responses");
            if (!responses.isObject() || responses.isEmpty()) {
                violations.add(violation("ERROR", "operation-responses",
                        pathKey + "." + method,
                        "Operation must define at least one response"));
            }
        });
    }

    // ── Rule 4: Path parameters documented ──────────────────────────────

    private void checkPathParamsDocumented(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            String pathKey = pathEntry.getKey();
            JsonNode pathNode = pathEntry.getValue();

            // Extract path parameter names from the path template
            Set<String> templateParams = extractPathParams(pathKey);
            if (templateParams.isEmpty()) continue;

            // Collect path-level parameters
            Set<String> pathLevelParams = collectDeclaredPathParams(pathNode.path("parameters"));

            // Check each operation
            Iterator<Map.Entry<String, JsonNode>> methods = pathNode.fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methods.next();
                String method = methodEntry.getKey();
                if (!HTTP_METHODS.contains(method.toLowerCase())) continue;

                JsonNode operation = methodEntry.getValue();
                if (!operation.isObject()) continue;

                Set<String> opParams = collectDeclaredPathParams(operation.path("parameters"));
                Set<String> allDeclared = new HashSet<>(pathLevelParams);
                allDeclared.addAll(opParams);

                for (String param : templateParams) {
                    if (!allDeclared.contains(param)) {
                        violations.add(violation("ERROR", "path-param-documented",
                                pathKey + "." + method + ".parameters." + param,
                                "Path parameter '{" + param + "}' is not documented in parameters"));
                    }
                }
            }
        }
    }

    // ── Rule 5: Contact info present ────────────────────────────────────

    private void checkContactInfo(JsonNode root, List<LintViolation> violations) {
        JsonNode contact = root.path("info").path("contact");
        if (contact.isMissingNode() || !contact.isObject() || contact.isEmpty()) {
            violations.add(violation("WARNING", "info-contact",
                    "/info/contact",
                    "Spec should include contact information (name, email, or url)"));
        }
    }

    // ── Rule 6: License info present ────────────────────────────────────

    private void checkLicenseInfo(JsonNode root, List<LintViolation> violations) {
        JsonNode license = root.path("info").path("license");
        if (license.isMissingNode() || !license.isObject() || license.isEmpty()) {
            violations.add(violation("INFO", "info-license",
                    "/info/license",
                    "Spec should include license information"));
        }
    }

    // ── Rule 7: Kebab-case paths ────────────────────────────────────────

    private void checkKebabCasePaths(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String pathName = pathNames.next();
            if (!KEBAB_CASE.matcher(pathName).matches()) {
                String withoutParams = pathName.replaceAll("/\\{[^}]+}", "");
                if (!withoutParams.isEmpty()
                        && !withoutParams.matches("^(/[a-z0-9]+(-[a-z0-9]+)*)*$")) {
                    violations.add(violation("WARNING", "path-kebab-case", pathName,
                            "Path should use lowercase kebab-case"));
                }
            }
        }
    }

    // ── Rule 8: Operation IDs ───────────────────────────────────────────

    private void checkOperationIds(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        forEachOperation(paths, (pathKey, method, operation) -> {
            String operationId = operation.path("operationId").asText("");
            if (operationId.isBlank()) {
                violations.add(violation("WARNING", "operation-operationId",
                        pathKey + "." + method,
                        "Operation should have an operationId for SDK generation"));
            }
        });
    }

    // ── Rule 9: Security schemes defined ────────────────────────────────

    private void checkSecuritySchemes(JsonNode root, List<LintViolation> violations) {
        JsonNode securitySchemes = root.path("components").path("securitySchemes");
        if (securitySchemes.isMissingNode() || !securitySchemes.isObject() || securitySchemes.isEmpty()) {
            violations.add(violation("ERROR", "security-scheme-defined",
                    "/components/securitySchemes",
                    "API must define at least one security scheme"));
        }

        JsonNode security = root.path("security");
        if (security.isMissingNode() || !security.isArray() || security.isEmpty()) {
            violations.add(violation("WARNING", "global-security",
                    "/security",
                    "API should define global security requirements"));
        }
    }

    // ── Rule 10: Error responses ────────────────────────────────────────

    private void checkErrorResponses(JsonNode root, List<LintViolation> violations) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) return;

        forEachOperation(paths, (pathKey, method, operation) -> {
            JsonNode responses = operation.path("responses");
            if (!responses.isObject()) return;

            boolean hasErrorResponse = false;
            Iterator<String> statusCodes = responses.fieldNames();
            while (statusCodes.hasNext()) {
                String code = statusCodes.next();
                if (code.startsWith("4") || code.startsWith("5") || "default".equals(code)) {
                    hasErrorResponse = true;
                    break;
                }
            }

            if (!hasErrorResponse) {
                violations.add(violation("WARNING", "error-response-schema",
                        pathKey + "." + method + ".responses",
                        "Responses should include at least one error schema (4xx/5xx or default)"));
            }
        });
    }

    // ── Score calculation ────────────────────────────────────────────────

    private int calculateLintScore(List<LintViolation> violations) {
        int totalPenalty = 0;
        for (LintViolation v : violations) {
            totalPenalty += switch (v.getSeverity()) {
                case "ERROR" -> ERROR_WEIGHT;
                case "WARNING" -> WARNING_WEIGHT;
                case "INFO" -> INFO_WEIGHT;
                default -> 0;
            };
        }
        return Math.max(0, 100 - totalPenalty);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static LintViolation violation(String severity, String rule, String path, String message) {
        return LintViolation.builder()
                .severity(severity)
                .rule(rule)
                .path(path)
                .message(message)
                .build();
    }

    private Set<String> extractPathParams(String path) {
        Set<String> params = new LinkedHashSet<>();
        int start = -1;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '{') {
                start = i + 1;
            } else if (path.charAt(i) == '}' && start >= 0) {
                params.add(path.substring(start, i));
                start = -1;
            }
        }
        return params;
    }

    private Set<String> collectDeclaredPathParams(JsonNode parametersNode) {
        Set<String> names = new HashSet<>();
        if (parametersNode == null || !parametersNode.isArray()) return names;

        for (JsonNode param : parametersNode) {
            String in = param.path("in").asText("");
            String name = param.path("name").asText("");
            if ("path".equals(in) && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    @FunctionalInterface
    private interface OperationVisitor {
        void visit(String pathKey, String method, JsonNode operation);
    }

    private void forEachOperation(JsonNode paths, OperationVisitor visitor) {
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            String pathKey = pathEntry.getKey();
            JsonNode pathNode = pathEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> methods = pathNode.fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methods.next();
                String method = methodEntry.getKey();
                if (!HTTP_METHODS.contains(method.toLowerCase())) continue;

                JsonNode operation = methodEntry.getValue();
                if (!operation.isObject()) continue;

                visitor.visit(pathKey, method, operation);
            }
        }
    }
}
