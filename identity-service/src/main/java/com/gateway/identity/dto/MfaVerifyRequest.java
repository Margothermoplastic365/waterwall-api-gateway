package com.gateway.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyRequest {

    /** 6-digit TOTP code or a recovery code. */
    @NotBlank(message = "MFA code is required")
    private String code;
}
