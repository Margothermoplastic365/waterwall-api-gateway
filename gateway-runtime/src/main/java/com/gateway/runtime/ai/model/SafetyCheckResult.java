package com.gateway.runtime.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of content safety analysis on a prompt or response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyCheckResult {

    private boolean blocked;
    private List<String> violations;
    private String redactedContent;
    private String severity;
}
