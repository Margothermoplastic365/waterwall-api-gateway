package com.gateway.notification.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationPreferenceRequest {

    private boolean emailEnabled;
    private boolean inAppEnabled;
    private boolean webhookEnabled;
    private List<String> mutedEventTypes;
}
