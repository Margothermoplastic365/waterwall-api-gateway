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
@Table(name = "compliance_reports", schema = "gateway")
public class ComplianceReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "score")
    private Integer score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb")
    private String findings;

    @Column(name = "generated_at")
    private Instant generatedAt;
}
