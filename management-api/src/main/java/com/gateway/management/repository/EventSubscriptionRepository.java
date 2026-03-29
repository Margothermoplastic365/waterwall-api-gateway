package com.gateway.management.repository;

import com.gateway.management.entity.EventSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventSubscriptionRepository extends JpaRepository<EventSubscriptionEntity, UUID> {

    List<EventSubscriptionEntity> findByEventApiId(UUID eventApiId);

    List<EventSubscriptionEntity> findByConsumerId(UUID consumerId);

    List<EventSubscriptionEntity> findByEventApiIdAndStatus(UUID eventApiId, String status);
}
