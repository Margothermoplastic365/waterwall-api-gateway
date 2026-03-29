package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_deployments", schema = "gateway")
public class ApiDeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "environment_slug", nullable = false, length = 50)
    private String environmentSlug;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "DEPLOYED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb")
    private String configSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upstream_overrides", columnDefinition = "jsonb")
    private String upstreamOverrides;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rate_limit_override", columnDefinition = "jsonb")
    private String rateLimitOverride;

    @Column(name = "auth_enforcement", length = 20)
    private String authEnforcement;

    @Column(name = "deployed_by")
    private UUID deployedBy;

    @Column(name = "deployed_at")
    @Builder.Default
    private Instant deployedAt = Instant.now();
}
