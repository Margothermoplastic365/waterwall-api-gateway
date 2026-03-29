package com.gateway.management.entity;

import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.entity.enums.Sensitivity;
import com.gateway.management.entity.enums.VersionStatus;
import com.gateway.management.entity.enums.Visibility;
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
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "apis", schema = "gateway")
public class ApiEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApiStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20)
    private Visibility visibility;

    @Column(name = "protocol_type", length = 20)
    private String protocolType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "auth_mode", length = 10)
    @Builder.Default
    private String authMode = "ANY";

    @Column(name = "allow_anonymous")
    @Builder.Default
    private Boolean allowAnonymous = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_config", columnDefinition = "jsonb")
    private String gatewayConfig;

    @Column(name = "backend_base_url", length = 1024)
    private String backendBaseUrl;

    // ── Versioning ──────────────────────────────────────────────

    @Column(name = "api_group_id")
    private UUID apiGroupId;

    @Column(name = "api_group_name", length = 255)
    private String apiGroupName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", length = 10)
    @Builder.Default
    private Sensitivity sensitivity = Sensitivity.LOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", length = 20)
    @Builder.Default
    private VersionStatus versionStatus = VersionStatus.ACTIVE;

    @Column(name = "deprecated_message", columnDefinition = "text")
    private String deprecatedMessage;

    @Column(name = "successor_version_id")
    private UUID successorVersionId;

    // ── Audit ────────────────────────────────────────────────

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
