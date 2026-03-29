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
@Table(name = "policy_attachments", schema = "gateway")
public class PolicyAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_policyattach_policy"))
    private PolicyEntity policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_id", foreignKey = @ForeignKey(name = "fk_policyattach_api"))
    private ApiEntity api;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", foreignKey = @ForeignKey(name = "fk_policyattach_route"))
    private RouteEntity route;

    @Column(name = "scope", length = 20)
    private String scope;

    @Column(name = "priority")
    private Integer priority;
}
