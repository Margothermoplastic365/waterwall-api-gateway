package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "data_classifications", schema = "gateway")
public class DataClassificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "classification", nullable = false, length = 50)
    private String classification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pii_fields", columnDefinition = "jsonb")
    private String piiFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "handling_policy", columnDefinition = "jsonb")
    private String handlingPolicy;
}
