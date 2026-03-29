package com.gateway.analytics.repository;

import com.gateway.analytics.entity.SlaConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfigEntity, UUID> {

    List<SlaConfigEntity> findByEnabledTrue();

    List<SlaConfigEntity> findByApiId(UUID apiId);
}
