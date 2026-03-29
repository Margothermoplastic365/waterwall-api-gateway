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
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mock_configs", schema = "gateway")
public class MockConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "status_code")
    @Builder.Default
    private int statusCode = 200;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "jsonb")
    private Map<String, String> responseHeaders;

    @Column(name = "latency_ms")
    @Builder.Default
    private int latencyMs = 0;

    @Column(name = "error_rate_percent")
    @Builder.Default
    private int errorRatePercent = 0;

    @Column(name = "mock_enabled")
    @Builder.Default
    private boolean mockEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
