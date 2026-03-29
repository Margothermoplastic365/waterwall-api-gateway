package com.gateway.runtime.ai.service;

import com.gateway.runtime.ai.config.LlmProviderConfig;
import com.gateway.runtime.ai.model.SafetyCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Content safety service providing prompt injection detection, PII detection,
 * and toxicity keyword filtering for LLM requests and responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSafetyService {

    private final LlmProviderConfig config;

    // ── Prompt injection patterns ───────────────────────────────────────

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)ignore\\s+(all\\s+)?above\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?previous"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?previous"),
            Pattern.compile("(?i)override\\s+system\\s+prompt"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+"),
            Pattern.compile("(?i)new\\s+instructions?:"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\[system\\]"),
            Pattern.compile("(?i)\\{\\{system\\}\\}"),
            Pattern.compile("(?i)<\\s*system\\s*>"),
            Pattern.compile("(?i)role\\s*:\\s*system"),
            Pattern.compile("(?i)pretend\\s+you\\s+are"),
            Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+restrictions"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)DAN\\s+mode"),
            Pattern.compile("(?i)developer\\s+mode\\s+enabled")
    );

    // ── PII patterns ────────────────────────────────────────────────────

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(\\+?1?[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}[- ]?\\d{2}[- ]?\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b");

    // ── Toxicity keywords ───────────────────────────────────────────────

    private static final Set<String> TOXICITY_KEYWORDS = Set.of(
            "kill", "murder", "bomb", "terrorist", "hack into",
            "exploit vulnerability", "make a weapon", "synthesize drugs",
            "build explosive"
    );

    /**
     * Check a user prompt for safety violations.
     */
    public SafetyCheckResult checkPrompt(String prompt) {
        List<String> violations = new ArrayList<>();
        String redactedContent = prompt;
        boolean blocked = false;
        String severity = "NONE";

        if (prompt == null || prompt.isBlank()) {
            return SafetyCheckResult.builder()
                    .blocked(false)
                    .violations(List.of())
                    .redactedContent(prompt)
                    .severity("NONE")
                    .build();
        }

        // 1. Prompt injection detection
        if (config.getSafety().isBlockOnInjection()) {
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(prompt).find()) {
                    violations.add("PROMPT_INJECTION: " + pattern.pattern());
                    blocked = true;
                    severity = "HIGH";
                    break;
                }
            }
        }

        // 2. PII detection
        boolean piiFound = false;
        if (EMAIL_PATTERN.matcher(prompt).find()) {
            violations.add("PII_DETECTED: email address");
            piiFound = true;
        }
        if (PHONE_PATTERN.matcher(prompt).find()) {
            violations.add("PII_DETECTED: phone number");
            piiFound = true;
        }
        if (SSN_PATTERN.matcher(prompt).find()) {
            violations.add("PII_DETECTED: SSN");
            piiFound = true;
        }
        if (CREDIT_CARD_PATTERN.matcher(prompt).find()) {
            violations.add("PII_DETECTED: credit card number");
            piiFound = true;
        }

        if (piiFound && config.getSafety().isRedactPii()) {
            redactedContent = redactPii(prompt);
            if (severity.equals("NONE")) {
                severity = "MEDIUM";
            }
        }

        // 3. Toxicity check
        if (config.getSafety().isBlockOnToxicity()) {
            String lowerPrompt = prompt.toLowerCase();
            for (String keyword : TOXICITY_KEYWORDS) {
                if (lowerPrompt.contains(keyword)) {
                    violations.add("TOXICITY: " + keyword);
                    blocked = true;
                    severity = "HIGH";
                    break;
                }
            }
        }

        return SafetyCheckResult.builder()
                .blocked(blocked)
                .violations(violations)
                .redactedContent(redactedContent)
                .severity(severity)
                .build();
    }

    /**
     * Check an LLM response for safety violations (PII leaks, harmful content).
     */
    public SafetyCheckResult checkResponse(String response) {
        List<String> violations = new ArrayList<>();
        String redactedContent = response;
        String severity = "NONE";

        if (response == null || response.isBlank()) {
            return SafetyCheckResult.builder()
                    .blocked(false)
                    .violations(List.of())
                    .redactedContent(response)
                    .severity("NONE")
                    .build();
        }

        // PII leak detection in response
        boolean piiFound = false;
        if (EMAIL_PATTERN.matcher(response).find()) {
            violations.add("PII_LEAK: email address in response");
            piiFound = true;
        }
        if (SSN_PATTERN.matcher(response).find()) {
            violations.add("PII_LEAK: SSN in response");
            piiFound = true;
        }
        if (CREDIT_CARD_PATTERN.matcher(response).find()) {
            violations.add("PII_LEAK: credit card number in response");
            piiFound = true;
        }

        if (piiFound && config.getSafety().isRedactPii()) {
            redactedContent = redactPii(response);
            severity = "MEDIUM";
        }

        return SafetyCheckResult.builder()
                .blocked(false)
                .violations(violations)
                .redactedContent(redactedContent)
                .severity(severity)
                .build();
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private String redactPii(String text) {
        String redacted = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL_REDACTED]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[PHONE_REDACTED]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[SSN_REDACTED]");
        redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("[CC_REDACTED]");
        return redacted;
    }
}
