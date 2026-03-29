package com.gateway.management.repository;

import com.gateway.management.entity.AiPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiPlanRepository extends JpaRepository<AiPlanEntity, UUID> {

    Optional<AiPlanEntity> findByName(String name);
}
