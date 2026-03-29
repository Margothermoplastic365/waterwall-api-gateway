package com.gateway.identity.repository;

import com.gateway.identity.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

    Optional<PermissionEntity> findByResourceAndAction(String resource, String action);

    List<PermissionEntity> findByResource(String resource);
}
