package com.gateway.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webhook_delivery_log", schema = "notification")
public class WebhookDeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_endpoint_id", nullable = false)
    private UUID webhookEndpointId;

    @Column(name = "event_type", length = 255)
    private String eventType;

    @Column(name = "status_code")
    private int statusCode;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;
}
