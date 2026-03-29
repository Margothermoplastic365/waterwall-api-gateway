package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_gateway_settings", schema = "gateway")
public class PaymentGatewaySettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, unique = true, length = 50)
    private String provider;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "environment", length = 20)
    private String environment;

    @Column(name = "secret_key", length = 500)
    private String secretKey;

    @Column(name = "public_key", length = 500)
    private String publicKey;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "supported_currencies", length = 500)
    private String supportedCurrencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_config", columnDefinition = "jsonb")
    private String extraConfig;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
