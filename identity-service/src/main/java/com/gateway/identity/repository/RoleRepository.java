package com.gateway.identity.repository;

import com.gateway.identity.entity.RoleEntity;
import com.gateway.identity.entity.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByName(String name);

    List<RoleEntity> findByScopeType(ScopeType scopeType);

    List<RoleEntity> findByIsSystemTrue();
}
