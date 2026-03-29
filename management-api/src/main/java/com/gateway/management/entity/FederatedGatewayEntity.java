package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "federated_gateways", schema = "gateway")
public class FederatedGatewayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "api_url", nullable = false, length = 1024)
    private String apiUrl;

    @Column(name = "api_key_encrypted", length = 2048)
    private String apiKeyEncrypted;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
