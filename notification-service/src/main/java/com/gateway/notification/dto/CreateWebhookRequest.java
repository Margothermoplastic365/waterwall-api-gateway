package com.gateway.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWebhookRequest {

    @NotBlank(message = "Webhook URL is required")
    private String url;

    /**
     * HMAC secret for signing payloads. If empty, one will be auto-generated.
     */
    private String secret;
}
