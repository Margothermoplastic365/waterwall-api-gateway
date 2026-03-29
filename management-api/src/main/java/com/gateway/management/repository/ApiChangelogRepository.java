package com.gateway.management.repository;

import com.gateway.management.entity.ApiChangelogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiChangelogRepository extends JpaRepository<ApiChangelogEntity, UUID> {

    List<ApiChangelogEntity> findByApiIdOrderByCreatedAtDesc(UUID apiId);

    Optional<ApiChangelogEntity> findByApiIdAndVersionFromAndVersionTo(UUID apiId, String versionFrom, String versionTo);

    List<ApiChangelogEntity> findByApiIdAndVersionTo(UUID apiId, String versionTo);
}
