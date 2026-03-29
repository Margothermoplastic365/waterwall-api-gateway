package com.gateway.management.repository;

import com.gateway.management.entity.ConsumerAlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerAlertRuleRepository extends JpaRepository<ConsumerAlertRuleEntity, UUID> {

    List<ConsumerAlertRuleEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ConsumerAlertRuleEntity> findByIdAndUserId(UUID id, UUID userId);

    List<ConsumerAlertRuleEntity> findByEnabledTrue();
}
