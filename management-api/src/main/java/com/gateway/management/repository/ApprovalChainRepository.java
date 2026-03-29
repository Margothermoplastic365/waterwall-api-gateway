package com.gateway.management.repository;

import com.gateway.management.entity.ApprovalChainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalChainRepository extends JpaRepository<ApprovalChainEntity, UUID> {

    List<ApprovalChainEntity> findByApiIdOrderByLevelAsc(UUID apiId);

    Optional<ApprovalChainEntity> findByApiIdAndLevel(UUID apiId, int level);

    void deleteByApiId(UUID apiId);
}
