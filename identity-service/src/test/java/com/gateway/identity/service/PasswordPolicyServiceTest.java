package com.gateway.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    void setUp() {
        // PasswordPolicyService has no injected dependencies; uses a no-arg constructor
        passwordPolicyService = new PasswordPolicyService();
    }

    // ── validate: valid passwords ───────────────────────────────────────

    @Test
    void validate_strongPassword_doesNotThrow() {
        assertThatCode(() -> passwordPolicyService.validate("StrongP@ss1"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_minimumValidPassword_doesNotThrow() {
        // 8 chars: 1 upper, 1 lower, 1 digit, 1 special
        assertThatCode(() -> passwordPolicyService.validate("Abcdef1!"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_longPassword_doesNotThrow() {
        String longPassword = "A" + "a".repeat(120) + "1!5678";
        assertThatCode(() -> passwordPolicyService.validate(longPassword))
                .doesNotThrowAnyException();
    }

    // ── validate: too short ─────────────────────────────────────────────

    @Test
    void validate_tooShort_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Ab1!"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    void validate_sevenChars_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Abcde1!"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: missing uppercase ─────────────────────────────────────

    @Test
    void validate_noUppercase_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("abcdefg1!"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: missing lowercase ─────────────────────────────────────

    @Test
    void validate_noLowercase_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("ABCDEFG1!"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: missing digit ─────────────────────────────────────────

    @Test
    void validate_noDigit_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Abcdefgh!"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: missing special character ──────────────────────────────

    @Test
    void validate_noSpecialChar_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Abcdefg12"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: whitespace ────────────────────────────────────────────

    @Test
    void validate_containsWhitespace_throwsPasswordPolicyException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Strong P@ss1"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── validate: exception contains violations ─────────────────────────

    @Test
    void validate_multipleViolations_exceptionContainsAllViolations() {
        try {
            passwordPolicyService.validate("abc");
        } catch (PasswordPolicyException ex) {
            // Should have violations for: too short, no uppercase, no digit, no special
            assertThat(ex.getViolations()).isNotEmpty();
            assertThat(ex.getViolations().size()).isGreaterThanOrEqualTo(3);
        }
    }

    // ── validate: parameterized invalid passwords ───────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "",          // empty
            "short",     // too short, missing digit/special/upper
            "12345678",  // no letters or special
            "ALLCAPS1!", // no lowercase
    })
    void validate_invalidPasswords_throwsPasswordPolicyException(String password) {
        assertThatThrownBy(() -> passwordPolicyService.validate(password))
                .isInstanceOf(PasswordPolicyException.class);
    }

    // ── encode and matches tests ────────────────────────────────────────

    @Test
    void encode_returnsNonNullPrefixedHash() {
        String encoded = passwordPolicyService.encode("StrongP@ss1");

        assertThat(encoded).isNotNull();
        assertThat(encoded).startsWith("{argon2}");
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        String raw = "StrongP@ss1";
        String encoded = passwordPolicyService.encode(raw);

        assertThat(passwordPolicyService.matches(raw, encoded)).isTrue();
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        String encoded = passwordPolicyService.encode("StrongP@ss1");

        assertThat(passwordPolicyService.matches("WrongP@ss1", encoded)).isFalse();
    }

    // ── isInHistory tests ───────────────────────────────────────────────

    @Test
    void isInHistory_passwordInHistory_returnsTrue() {
        String raw = "StrongP@ss1";
        String hash1 = passwordPolicyService.encode(raw);
        String hash2 = passwordPolicyService.encode("OtherP@ss2");

        boolean result = passwordPolicyService.isInHistory(raw, List.of(hash2, hash1));

        assertThat(result).isTrue();
    }

    @Test
    void isInHistory_passwordNotInHistory_returnsFalse() {
        String hash1 = passwordPolicyService.encode("OldP@ss1");
        String hash2 = passwordPolicyService.encode("OldP@ss2");

        boolean result = passwordPolicyService.isInHistory("BrandN3w!", List.of(hash1, hash2));

        assertThat(result).isFalse();
    }

    @Test
    void isInHistory_nullHistory_returnsFalse() {
        boolean result = passwordPolicyService.isInHistory("StrongP@ss1", null);

        assertThat(result).isFalse();
    }

    @Test
    void isInHistory_emptyHistory_returnsFalse() {
        boolean result = passwordPolicyService.isInHistory("StrongP@ss1", List.of());

        assertThat(result).isFalse();
    }

    // ── getPasswordEncoder tests ────────────────────────────────────────

    @Test
    void getPasswordEncoder_returnsNonNull() {
        assertThat(passwordPolicyService.getPasswordEncoder()).isNotNull();
    }
}
