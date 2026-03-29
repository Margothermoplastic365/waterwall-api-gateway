package com.gateway.management.repository;

import com.gateway.management.entity.FederatedGatewayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FederatedGatewayRepository extends JpaRepository<FederatedGatewayEntity, UUID> {

    List<FederatedGatewayEntity> findByStatus(String status);

    List<FederatedGatewayEntity> findByType(String type);
}
