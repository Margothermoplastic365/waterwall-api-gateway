package com.gateway.management.repository;

import com.gateway.management.entity.DeveloperSandboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeveloperSandboxRepository extends JpaRepository<DeveloperSandboxEntity, UUID> {

    Optional<DeveloperSandboxEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    List<DeveloperSandboxEntity> findByExpiresAtBefore(Instant cutoff);
}
