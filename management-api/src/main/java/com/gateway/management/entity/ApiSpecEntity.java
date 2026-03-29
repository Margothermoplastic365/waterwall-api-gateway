package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_specs", schema = "gateway")
public class ApiSpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false, unique = true)
    private UUID apiId;

    @Column(name = "spec_content", columnDefinition = "text")
    private String specContent;

    @Column(name = "spec_format", length = 30)
    @Builder.Default
    private String specFormat = "OPENAPI_3";

    @Column(name = "lint_score")
    private Integer lintScore;

    @Column(name = "last_linted_at")
    private Instant lastLintedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
