package com.gateway.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "metrics_1h", schema = "analytics",
        uniqueConstraints = @UniqueConstraint(name = "uq_metrics1h", columnNames = {"api_id", "window_start"}))
public class Metrics1hEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_id")
    private UUID apiId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "request_count")
    private int requestCount;

    @Column(name = "error_count")
    private int errorCount;

    @Column(name = "latency_sum_ms")
    private long latencySumMs;

    @Column(name = "latency_max_ms")
    private int latencyMaxMs;
}
