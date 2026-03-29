package com.gateway.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    private String title;
    private String body;
    private String type;
    private boolean read;
    private Instant createdAt;
}
