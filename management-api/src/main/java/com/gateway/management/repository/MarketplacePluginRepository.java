package com.gateway.management.repository;

import com.gateway.management.entity.MarketplacePluginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplacePluginRepository extends JpaRepository<MarketplacePluginEntity, UUID> {

    List<MarketplacePluginEntity> findByType(String type);

    List<MarketplacePluginEntity> findByNameContainingIgnoreCase(String name);

    List<MarketplacePluginEntity> findByCertifiedTrue();
}
