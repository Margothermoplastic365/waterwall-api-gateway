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
@Table(name = "status_page_entries", schema = "gateway")
public class StatusPageEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
