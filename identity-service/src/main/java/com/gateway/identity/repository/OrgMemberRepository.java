package com.gateway.identity.repository;

import com.gateway.identity.entity.OrgMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgMemberRepository extends JpaRepository<OrgMemberEntity, UUID> {

    List<OrgMemberEntity> findByUserId(UUID userId);

    List<OrgMemberEntity> findByOrganizationId(UUID orgId);

    Optional<OrgMemberEntity> findByUserIdAndOrganizationId(UUID userId, UUID orgId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID orgId);
}
