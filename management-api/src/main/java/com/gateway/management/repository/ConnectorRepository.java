package com.gateway.management.repository;

import com.gateway.management.entity.ConnectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectorRepository extends JpaRepository<ConnectorEntity, UUID> {

    List<ConnectorEntity> findByType(String type);

    List<ConnectorEntity> findByEnabled(Boolean enabled);
}
