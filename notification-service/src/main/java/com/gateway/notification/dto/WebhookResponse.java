package com.gateway.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WebhookResponse {

    private UUID id;
    private String url;
    private boolean active;
    private Instant createdAt;
}
