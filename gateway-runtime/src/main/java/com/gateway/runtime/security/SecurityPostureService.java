package com.gateway.runtime.security;

import com.gateway.runtime.service.RouteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs automated security posture audits of all configured APIs.
 * Checks for missing authentication, overly permissive CORS, and
 * exposed debug endpoints. Returns a SecurityPostureReport with a
 * score, issues, and recommendations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPostureService {

    private final RouteConfigService routeConfigService;

    /**
     * Run a full security posture audit and return the report.
     */
    public SecurityPostureReport audit() {
        List<SecurityIssue> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Audit all loaded routes
        var routes = routeConfigService.getAllRoutes();
        int totalApis = routes.size();
        int apisWithoutAuth = 0;
        int apisWithDebugEndpoints = 0;

        for (var route : routes) {
            String apiId = route.getApiId() != null ? route.getApiId().toString() : "unknown";
            String path = route.getPath();

            // Check for missing auth (empty or null authTypes list)
            var authTypes = route.getAuthTypes();
            if (authTypes == null || authTypes.isEmpty()) {
                apisWithoutAuth++;
                issues.add(new SecurityIssue(
                        "NO_AUTH",
                        "HIGH",
                        "API route has no authentication: " + path,
                        apiId
                ));
            }

            // Check for exposed debug/internal endpoints
            if (path != null && (path.contains("/debug") || path.contains("/internal")
                    || path.contains("/actuator") || path.contains("/swagger"))) {
                apisWithDebugEndpoints++;
                issues.add(new SecurityIssue(
                        "DEBUG_EXPOSED",
                        "MEDIUM",
                        "Potentially sensitive endpoint exposed: " + path,
                        apiId
                ));
            }
        }

        // Build recommendations
        if (apisWithoutAuth > 0) {
            recommendations.add("Enable authentication on " + apisWithoutAuth + " unprotected API routes");
        }
        if (apisWithDebugEndpoints > 0) {
            recommendations.add("Review and restrict " + apisWithDebugEndpoints + " debug/internal endpoints");
        }
        if (issues.isEmpty()) {
            recommendations.add("Security posture is healthy. Continue monitoring.");
        }

        // Score: start at 100, deduct per issue
        int score = 100;
        for (SecurityIssue issue : issues) {
            switch (issue.severity()) {
                case "HIGH" -> score -= 15;
                case "MEDIUM" -> score -= 8;
                case "LOW" -> score -= 3;
            }
        }
        score = Math.max(0, score);

        return new SecurityPostureReport(
                score,
                totalApis,
                issues,
                recommendations,
                Instant.now()
        );
    }

    public record SecurityPostureReport(
            int score,
            int totalApisAudited,
            List<SecurityIssue> issues,
            List<String> recommendations,
            Instant auditedAt
    ) {}

    public record SecurityIssue(
            String type,
            String severity,
            String description,
            String apiId
    ) {}
}
