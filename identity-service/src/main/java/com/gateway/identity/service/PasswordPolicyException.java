package com.gateway.identity.service;

import java.util.List;

/**
 * Thrown when a password does not meet the configured password policy rules.
 */
public class PasswordPolicyException extends RuntimeException {

    private final List<String> violations;

    public PasswordPolicyException(List<String> violations) {
        super("Password policy violated: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
