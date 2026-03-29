package com.gateway.management.repository;

import com.gateway.management.entity.RegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegionRepository extends JpaRepository<RegionEntity, UUID> {

    Optional<RegionEntity> findBySlug(String slug);

    List<RegionEntity> findByStatus(String status);

    List<RegionEntity> findByDataResidencyZone(String zone);
}
