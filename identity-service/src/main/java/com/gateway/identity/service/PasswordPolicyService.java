package com.gateway.identity.service;

import org.passay.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates passwords against the platform password policy and provides
 * encoding/matching using DelegatingPasswordEncoder with Argon2 as default.
 *
 * <p>Policy rules (aligned with section 3.1.3 of the requirements):
 * <ul>
 *   <li>Minimum length: 8</li>
 *   <li>Maximum length: 128</li>
 *   <li>At least 1 uppercase letter</li>
 *   <li>At least 1 lowercase letter</li>
 *   <li>At least 1 digit</li>
 *   <li>At least 1 special character</li>
 *   <li>No whitespace characters</li>
 * </ul>
 */
@Service
public class PasswordPolicyService {

    private final org.passay.PasswordValidator validator;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicyService() {
        this.validator = buildValidator();
        this.passwordEncoder = buildPasswordEncoder();
    }

    /**
     * Validates the given raw password against the password policy.
     *
     * @param password the raw password to validate
     * @throws PasswordPolicyException if one or more rules are violated
     */
    public void validate(String password) {
        RuleResult result = validator.validate(new PasswordData(password));
        if (!result.isValid()) {
            List<String> violations = validator.getMessages(result);
            throw new PasswordPolicyException(violations);
        }
    }

    /**
     * Checks whether the new raw password matches any of the previous password
     * hashes (password history). Returns {@code true} if the password was
     * already used (i.e. is in history and should be rejected).
     *
     * @param newPassword    the raw new password
     * @param previousHashes list of previously stored encoded password hashes
     * @return {@code true} if the password matches any previous hash
     */
    public boolean isInHistory(String newPassword, List<String> previousHashes) {
        if (previousHashes == null || previousHashes.isEmpty()) {
            return false;
        }
        for (String previousHash : previousHashes) {
            if (passwordEncoder.matches(newPassword, previousHash)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Encodes a raw password using the configured DelegatingPasswordEncoder
     * (Argon2 default).
     *
     * @param rawPassword the raw password to encode
     * @return the encoded password string (prefixed with algorithm identifier)
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Checks whether the given raw password matches the encoded password.
     *
     * @param raw     the raw password
     * @param encoded the encoded password (with algorithm prefix)
     * @return {@code true} if they match
     */
    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }

    /**
     * Exposes the underlying PasswordEncoder for use in Spring Security
     * configuration if needed.
     *
     * @return the configured DelegatingPasswordEncoder
     */
    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    // -------------------------------------------------------------------------
    //  Private helpers
    // -------------------------------------------------------------------------

    private org.passay.PasswordValidator buildValidator() {
        return new org.passay.PasswordValidator(List.of(
                // Minimum length 8
                new LengthRule(8, 128),
                // At least 1 uppercase letter
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                // At least 1 lowercase letter
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                // At least 1 digit
                new CharacterRule(EnglishCharacterData.Digit, 1),
                // At least 1 special character
                new CharacterRule(EnglishCharacterData.Special, 1),
                // No whitespace
                new WhitespaceRule()
        ));
    }

    private PasswordEncoder buildPasswordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();

        // Argon2 — default encoder
        // Parameters: saltLength=16, hashLength=32, parallelism=1, memory=19456 (19 MiB), iterations=2
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        encoders.put("argon2", argon2);

        // BCrypt — fallback for migrated passwords
        encoders.put("bcrypt", new BCryptPasswordEncoder());

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);
        delegating.setDefaultPasswordEncoderForMatches(argon2);
        return delegating;
    }
}
