package com.gateway.management.repository;

import com.gateway.management.entity.ApiSpecEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiSpecRepository extends JpaRepository<ApiSpecEntity, UUID> {

    Optional<ApiSpecEntity> findByApiId(UUID apiId);

    List<ApiSpecEntity> findByLintScoreIsNotNullOrderByLintScoreDesc();
}
