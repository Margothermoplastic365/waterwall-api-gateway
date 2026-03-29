package com.gateway.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {

    /** Base32-encoded TOTP secret key. */
    private String secretKey;

    /** QR code image as a data URL (data:image/png;base64,...). */
    private String qrCodeDataUrl;

    /** One-time recovery codes (shown once, store safely). */
    private List<String> recoveryCodes;
}
