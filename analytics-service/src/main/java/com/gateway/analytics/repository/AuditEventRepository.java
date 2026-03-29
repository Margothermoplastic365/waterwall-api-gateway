package com.gateway.analytics.repository;

import com.gateway.analytics.entity.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    Page<AuditEventEntity> findByActionContainingIgnoreCase(String action, Pageable pageable);

    Page<AuditEventEntity> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditEventEntity> findByResourceType(String resourceType, Pageable pageable);

    @Query("SELECT a FROM AuditEventEntity a WHERE " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to)")
    Page<AuditEventEntity> search(
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT a FROM AuditEventEntity a WHERE " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) " +
           "ORDER BY a.createdAt DESC")
    List<AuditEventEntity> searchForExport(
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
