package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_chains", schema = "gateway",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_chain_api_level", columnNames = {"api_id", "level"}))
public class ApprovalChainEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "api_id", nullable = false)
    private UUID apiId;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "required_permission", nullable = false, length = 100)
    private String requiredPermission;

    @Column(name = "description", length = 255)
    private String description;
}
