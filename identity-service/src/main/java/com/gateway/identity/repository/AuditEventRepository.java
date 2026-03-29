package com.gateway.identity.repository;

import com.gateway.identity.entity.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    Page<AuditEventEntity> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditEventEntity> findByAction(String action, Pageable pageable);

    Page<AuditEventEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);
}
