package com.gateway.analytics.repository;

import com.gateway.analytics.entity.SlaBreachEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlaBreachRepository extends JpaRepository<SlaBreachEntity, UUID> {

    List<SlaBreachEntity> findByApiIdAndBreachedAtAfterOrderByBreachedAtDesc(UUID apiId, Instant after);

    List<SlaBreachEntity> findByBreachedAtAfterOrderByBreachedAtDesc(Instant after);

    List<SlaBreachEntity> findBySlaConfigIdAndMetricAndResolvedAtIsNull(UUID slaConfigId, String metric);
}
