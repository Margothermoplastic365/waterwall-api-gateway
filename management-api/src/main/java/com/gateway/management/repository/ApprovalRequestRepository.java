package com.gateway.management.repository;

import com.gateway.management.entity.ApprovalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, UUID> {

    List<ApprovalRequestEntity> findByStatus(String status);

    List<ApprovalRequestEntity> findByResourceId(UUID resourceId);

    List<ApprovalRequestEntity> findAllByOrderByRequestedAtDesc();
}
