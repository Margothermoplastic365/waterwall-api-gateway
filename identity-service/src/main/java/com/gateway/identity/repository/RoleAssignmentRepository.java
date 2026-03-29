package com.gateway.identity.repository;

import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> {

    List<RoleAssignmentEntity> findByUserId(UUID userId);

    List<RoleAssignmentEntity> findByUserIdAndScopeType(UUID userId, ScopeType scopeType);

    List<RoleAssignmentEntity> findByUserIdAndScopeTypeAndScopeId(UUID userId, ScopeType scopeType, UUID scopeId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("SELECT ra FROM RoleAssignmentEntity ra " +
            "WHERE ra.user.id = :userId " +
            "AND (ra.expiresAt IS NULL OR ra.expiresAt > CURRENT_TIMESTAMP)")
    List<RoleAssignmentEntity> findActiveByUserId(@Param("userId") UUID userId);
}
