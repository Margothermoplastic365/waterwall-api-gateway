package com.gateway.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Partial login response returned when MFA is required.
 * The client must complete a second-factor challenge before receiving full JWT tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaLoginResponse {

    /** Indicates that MFA verification is required to complete login. */
    @Builder.Default
    private boolean mfaRequired = true;

    /** Temporary session token used to authenticate the MFA verification step. */
    private String mfaSessionToken;

    /** List of available MFA factor types the user has enabled (e.g. "TOTP", "EMAIL"). */
    private List<String> availableFactors;
}
