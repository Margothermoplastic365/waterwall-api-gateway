package com.gateway.management.repository;

import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    List<SubscriptionEntity> findByApplicationId(UUID applicationId);

    List<SubscriptionEntity> findByApiId(UUID apiId);

    Optional<SubscriptionEntity> findByApplicationIdAndApiId(UUID applicationId, UUID apiId);

    List<SubscriptionEntity> findByStatus(SubStatus status);

    List<SubscriptionEntity> findByExpiresAtBeforeAndStatusIn(java.time.Instant now, List<SubStatus> statuses);
}
