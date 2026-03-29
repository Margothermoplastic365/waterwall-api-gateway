package com.gateway.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "identity",
        uniqueConstraints = @UniqueConstraint(name = "uq_permission", columnNames = {"resource", "action"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "description", length = 255)
    private String description;
}
