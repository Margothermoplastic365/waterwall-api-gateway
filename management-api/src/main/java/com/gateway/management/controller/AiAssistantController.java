package com.gateway.management.controller;

import com.gateway.management.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI-assisted platform operations.
 * Provides natural-language-driven helpers for spec generation, policy suggestion,
 * spec linting, and mock generation.
 */
@RestController
@RequestMapping("/v1/ai/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    /**
     * Generate an OpenAPI spec from a natural language description.
     */
    @PostMapping("/generate-spec")
    public ResponseEntity<String> generateSpec(@RequestBody Map<String, String> request) {
        String description = request.getOrDefault("description", "");
        String spec = aiAssistantService.generateSpecFromDescription(description);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(spec);
    }

    /**
     * Suggest a policy configuration from a natural language description.
     */
    @PostMapping("/suggest-policy")
    public ResponseEntity<String> suggestPolicy(@RequestBody Map<String, String> request) {
        String description = request.getOrDefault("description", "");
        String policy = aiAssistantService.suggestPolicy(description);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(policy);
    }

    /**
     * Detect issues in an OpenAPI spec.
     */
    @PostMapping("/detect-issues")
    public ResponseEntity<List<String>> detectIssues(@RequestBody Map<String, String> request) {
        String spec = request.getOrDefault("spec", "");
        List<String> issues = aiAssistantService.detectSpecIssues(spec);
        return ResponseEntity.ok(issues);
    }

    /**
     * Generate mock response data from an OpenAPI spec.
     */
    @PostMapping("/generate-mock")
    public ResponseEntity<String> generateMock(@RequestBody Map<String, String> request) {
        String spec = request.getOrDefault("spec", "");
        String mock = aiAssistantService.generateMockFromSpec(spec);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(mock);
    }
}
