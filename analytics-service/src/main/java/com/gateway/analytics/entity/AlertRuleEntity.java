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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alert_rules", schema = "analytics")
public class AlertRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "metric", length = 100, nullable = false)
    private String metric;

    @Column(name = "condition", length = 20, nullable = false)
    private String condition;

    @Column(name = "threshold")
    private BigDecimal threshold;

    @Column(name = "window_minutes")
    private int windowMinutes;

    @Column(name = "api_id")
    private UUID apiId;

    @Column(name = "enabled")
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", columnDefinition = "jsonb")
    private String channels;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
