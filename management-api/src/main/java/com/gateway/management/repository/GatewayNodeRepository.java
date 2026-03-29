package com.gateway.management.repository;

import com.gateway.management.entity.GatewayNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GatewayNodeRepository extends JpaRepository<GatewayNodeEntity, UUID> {

    Optional<GatewayNodeEntity> findByHostname(String hostname);

    List<GatewayNodeEntity> findByStatus(String status);

    @Modifying
    @Query("UPDATE GatewayNodeEntity n SET n.status = 'DOWN' WHERE n.lastHeartbeat < :cutoff AND n.status <> 'DOWN'")
    int markStaleNodesDown(@Param("cutoff") Instant cutoff);
}
