package com.gateway.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponse {

    private boolean totpEnabled;

    private boolean emailOtpEnabled;

    private boolean smsOtpEnabled;

    private int recoveryCodesRemaining;
}
