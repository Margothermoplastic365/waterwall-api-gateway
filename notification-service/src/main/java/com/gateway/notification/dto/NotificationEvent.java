package com.gateway.notification.dto;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationEvent {

    private String eventId;
    private String eventType;
    private String recipientUserId;
    private String recipientEmail;
    private String templateName;
    private Map<String, Object> variables;
    private String channel;
}
