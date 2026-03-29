package com.gateway.management.repository;

import com.gateway.management.entity.DataClassificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataClassificationRepository extends JpaRepository<DataClassificationEntity, UUID> {

    Optional<DataClassificationEntity> findByApiId(UUID apiId);
}
