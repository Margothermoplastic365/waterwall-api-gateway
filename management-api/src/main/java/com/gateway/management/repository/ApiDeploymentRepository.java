package com.gateway.management.repository;

import com.gateway.management.entity.ApiDeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiDeploymentRepository extends JpaRepository<ApiDeploymentEntity, UUID> {

    List<ApiDeploymentEntity> findByApiId(UUID apiId);

    List<ApiDeploymentEntity> findByEnvironmentSlug(String slug);

    Optional<ApiDeploymentEntity> findByApiIdAndEnvironmentSlug(UUID apiId, String slug);

    long countByEnvironmentSlug(String slug);
}
