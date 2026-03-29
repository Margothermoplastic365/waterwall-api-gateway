package com.gateway.management.repository;

import com.gateway.management.entity.EnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    Optional<EnvironmentEntity> findBySlug(String slug);
}
