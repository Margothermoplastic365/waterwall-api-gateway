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
@Table(name = "migrations", schema = "gateway")
public class MigrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "source_env", nullable = false, length = 50)
    private String sourceEnv;

    @Column(name = "target_env", nullable = false, length = 50)
    private String targetEnv;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "migration_package", columnDefinition = "jsonb")
    private String migrationPackage;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "initiated_at")
    @Builder.Default
    private Instant initiatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
