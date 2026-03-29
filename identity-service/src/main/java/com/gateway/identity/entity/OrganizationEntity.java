package com.gateway.identity.entity;

import com.gateway.identity.entity.enums.OrgStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrgStatus status;

    @Column(name = "billing_contact_email", length = 255)
    private String billingContactEmail;

    @Column(name = "technical_contact_email", length = 255)
    private String technicalContactEmail;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
