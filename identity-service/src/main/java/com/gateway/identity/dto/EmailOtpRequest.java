package com.gateway.identity.dto;

import lombok.Data;

/**
 * Empty request body — simply triggers sending an OTP to the user's registered email.
 */
@Data
public class EmailOtpRequest {
    // No fields required — the authenticated user's ID is resolved from the security context.
}
