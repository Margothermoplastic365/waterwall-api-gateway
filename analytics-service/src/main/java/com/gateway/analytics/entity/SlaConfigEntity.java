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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sla_configs", schema = "analytics")
public class SlaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "api_name", nullable = false)
    private String apiName;

    @Column(name = "uptime_target", precision = 5, scale = 2)
    private BigDecimal uptimeTarget;

    @Column(name = "latency_target_ms")
    private Integer latencyTargetMs;

    @Column(name = "error_budget_pct", precision = 5, scale = 2)
    private BigDecimal errorBudgetPct;

    @Column(name = "enabled")
    private Boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
