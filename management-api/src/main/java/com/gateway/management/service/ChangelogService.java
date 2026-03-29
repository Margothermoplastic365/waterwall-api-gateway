package com.gateway.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.entity.ApiChangelogEntity;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.repository.ApiChangelogRepository;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generates and manages API version changelogs.
 * Compares two API spec versions and produces a structured diff
 * including breaking changes and migration guides.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangelogService {

    private final ApiChangelogRepository changelogRepository;
    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate a changelog between two API versions.
     */
    @Transactional
    public ApiChangelogEntity generateChangelog(UUID apiId, String fromVersion, String toVersion) {
        apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        // Check if changelog already exists
        Optional<ApiChangelogEntity> existing = changelogRepository
                .findByApiIdAndVersionFromAndVersionTo(apiId, fromVersion, toVersion);
        if (existing.isPresent()) {
            log.info("Changelog already exists for apiId={} from={} to={}", apiId, fromVersion, toVersion);
            return existing.get();
        }

        // Generate diff analysis
        List<Map<String, String>> changes = analyzeChanges(apiId, fromVersion, toVersion);
        List<Map<String, String>> breakingChanges = detectBreakingChanges(changes);
        String migrationGuide = buildMigrationGuide(apiId, fromVersion, toVersion, breakingChanges);

        String changesJson;
        String breakingJson;
        try {
            changesJson = objectMapper.writeValueAsString(changes);
            breakingJson = objectMapper.writeValueAsString(breakingChanges);
        } catch (Exception e) {
            changesJson = "[]";
            breakingJson = "[]";
        }

        ApiChangelogEntity changelog = ApiChangelogEntity.builder()
                .apiId(apiId)
                .versionFrom(fromVersion)
                .versionTo(toVersion)
                .changes(changesJson)
                .breakingChanges(breakingJson)
                .migrationGuide(migrationGuide)
                .build();

        ApiChangelogEntity saved = changelogRepository.save(changelog);
        log.info("Generated changelog for apiId={}: {} -> {} ({} changes, {} breaking)",
                apiId, fromVersion, toVersion, changes.size(), breakingChanges.size());
        return saved;
    }

    /**
     * List all changelogs for an API, ordered by creation date.
     */
    @Transactional(readOnly = true)
    public List<ApiChangelogEntity> listChangelogs(UUID apiId) {
        apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));
        return changelogRepository.findByApiIdOrderByCreatedAtDesc(apiId);
    }

    /**
     * Get breaking changes for a specific API version.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBreakingChanges(UUID apiId, String version) {
        apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        List<ApiChangelogEntity> changelogs = changelogRepository.findByApiIdAndVersionTo(apiId, version);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ApiChangelogEntity cl : changelogs) {
            try {
                if (cl.getBreakingChanges() != null && !cl.getBreakingChanges().isBlank()) {
                    List<?> breaking = objectMapper.readValue(cl.getBreakingChanges(), List.class);
                    result.add(Map.of(
                            "fromVersion", cl.getVersionFrom() != null ? cl.getVersionFrom() : "",
                            "toVersion", cl.getVersionTo() != null ? cl.getVersionTo() : "",
                            "breakingChanges", breaking,
                            "createdAt", cl.getCreatedAt() != null ? cl.getCreatedAt().toString() : ""
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to parse breaking changes for changelog {}: {}", cl.getId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Generate a migration guide between two versions.
     */
    @Transactional(readOnly = true)
    public String generateMigrationGuide(UUID apiId, String fromVersion, String toVersion) {
        apiRepository.findById(apiId)
                .orElseThrow(() -> new EntityNotFoundException("API not found: " + apiId));

        Optional<ApiChangelogEntity> changelog = changelogRepository
                .findByApiIdAndVersionFromAndVersionTo(apiId, fromVersion, toVersion);

        if (changelog.isPresent() && changelog.get().getMigrationGuide() != null) {
            return changelog.get().getMigrationGuide();
        }

        // Generate a basic migration guide
        return buildMigrationGuide(apiId, fromVersion, toVersion, Collections.emptyList());
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private List<Map<String, String>> analyzeChanges(UUID apiId, String fromVersion, String toVersion) {
        List<Map<String, String>> changes = new ArrayList<>();

        // Structural analysis based on version semantics
        String[] fromParts = fromVersion.replaceAll("[^0-9.]", "").split("\\.");
        String[] toParts = toVersion.replaceAll("[^0-9.]", "").split("\\.");

        int fromMajor = fromParts.length > 0 ? parseIntSafe(fromParts[0]) : 0;
        int toMajor = toParts.length > 0 ? parseIntSafe(toParts[0]) : 0;
        int fromMinor = fromParts.length > 1 ? parseIntSafe(fromParts[1]) : 0;
        int toMinor = toParts.length > 1 ? parseIntSafe(toParts[1]) : 0;

        if (toMajor > fromMajor) {
            changes.add(Map.of(
                    "type", "MAJOR_VERSION_BUMP",
                    "description", "Major version changed from " + fromMajor + " to " + toMajor,
                    "impact", "HIGH",
                    "category", "versioning"
            ));
        } else if (toMinor > fromMinor) {
            changes.add(Map.of(
                    "type", "MINOR_VERSION_BUMP",
                    "description", "Minor version changed from " + fromMinor + " to " + toMinor,
                    "impact", "MEDIUM",
                    "category", "versioning"
            ));
        } else {
            changes.add(Map.of(
                    "type", "PATCH_VERSION_BUMP",
                    "description", "Patch version updated from " + fromVersion + " to " + toVersion,
                    "impact", "LOW",
                    "category", "versioning"
            ));
        }

        // Check if spec content differs
        apiSpecRepository.findByApiId(apiId).ifPresent(spec -> {
            changes.add(Map.of(
                    "type", "SPEC_AVAILABLE",
                    "description", "API specification is available for detailed diff",
                    "impact", "INFO",
                    "category", "documentation"
            ));
        });

        return changes;
    }

    private List<Map<String, String>> detectBreakingChanges(List<Map<String, String>> changes) {
        List<Map<String, String>> breaking = new ArrayList<>();
        for (Map<String, String> change : changes) {
            if ("MAJOR_VERSION_BUMP".equals(change.get("type"))) {
                breaking.add(Map.of(
                        "type", "BREAKING",
                        "description", change.get("description"),
                        "recommendation", "Review all endpoints — major version bumps may include removed or renamed endpoints, changed request/response schemas, and modified authentication requirements."
                ));
            }
        }
        return breaking;
    }

    private String buildMigrationGuide(UUID apiId, String fromVersion, String toVersion,
                                       List<Map<String, String>> breakingChanges) {
        ApiEntity api = apiRepository.findById(apiId).orElse(null);
        String apiName = api != null ? api.getName() : "API";

        StringBuilder guide = new StringBuilder();
        guide.append("# Migration Guide: ").append(apiName).append("\n");
        guide.append("## From ").append(fromVersion).append(" to ").append(toVersion).append("\n\n");

        if (breakingChanges.isEmpty()) {
            guide.append("No breaking changes detected. This update should be backwards compatible.\n\n");
            guide.append("### Recommended Steps\n");
            guide.append("1. Update your client SDK version\n");
            guide.append("2. Run your test suite to verify compatibility\n");
            guide.append("3. Deploy to staging before production\n");
        } else {
            guide.append("### Breaking Changes\n\n");
            for (int i = 0; i < breakingChanges.size(); i++) {
                Map<String, String> bc = breakingChanges.get(i);
                guide.append(i + 1).append(". **").append(bc.get("type")).append("**: ");
                guide.append(bc.get("description")).append("\n");
                if (bc.containsKey("recommendation")) {
                    guide.append("   - ").append(bc.get("recommendation")).append("\n");
                }
            }
            guide.append("\n### Migration Steps\n");
            guide.append("1. Review the breaking changes listed above\n");
            guide.append("2. Update your client SDK to version ").append(toVersion).append("\n");
            guide.append("3. Update any hardcoded paths or schemas\n");
            guide.append("4. Run your full test suite\n");
            guide.append("5. Test in a sandbox environment first\n");
            guide.append("6. Deploy to staging, validate, then production\n");
        }

        return guide.toString();
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
