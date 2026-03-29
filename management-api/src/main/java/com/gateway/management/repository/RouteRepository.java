package com.gateway.management.repository;

import com.gateway.management.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {

    List<RouteEntity> findByApiId(UUID apiId);

    List<RouteEntity> findByEnabledTrue();

    Optional<RouteEntity> findByApiIdAndPathAndMethod(UUID apiId, String path, String method);
}
