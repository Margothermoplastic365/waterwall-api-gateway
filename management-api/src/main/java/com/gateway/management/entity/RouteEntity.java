package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "routes", schema = "gateway")
public class RouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_api"))
    private ApiEntity api;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "upstream_url", nullable = false, length = 1000)
    private String upstreamUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_types", columnDefinition = "jsonb")
    private List<String> authTypes;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "strip_prefix")
    private Boolean stripPrefix;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "require_mfa")
    @Builder.Default
    private Boolean requireMfa = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
