package com.gateway.management.repository;

import com.gateway.management.entity.PolicyAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyAttachmentRepository extends JpaRepository<PolicyAttachmentEntity, UUID> {

    List<PolicyAttachmentEntity> findByApi_Id(UUID apiId);

    List<PolicyAttachmentEntity> findByRoute_Id(UUID routeId);

    List<PolicyAttachmentEntity> findByPolicy_Id(UUID policyId);
}
