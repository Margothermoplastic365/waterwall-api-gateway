package com.gateway.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sla_breaches", schema = "analytics")
public class SlaBreachEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "sla_config_id", nullable = false)
    private UUID slaConfigId;

    @Column(name = "metric", length = 50, nullable = false)
    private String metric;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "actual_value")
    private BigDecimal actualValue;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "breached_at", nullable = false)
    private Instant breachedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
