package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gateway_nodes", schema = "gateway")
public class GatewayNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hostname", nullable = false, unique = true, length = 255)
    private String hostname;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "config_version")
    @Builder.Default
    private Long configVersion = 0L;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "UP";

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "registered_at")
    private Instant registeredAt;
}
