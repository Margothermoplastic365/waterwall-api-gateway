package com.gateway.management.repository;

import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentGatewaySettingsRepository extends JpaRepository<PaymentGatewaySettingsEntity, UUID> {

    Optional<PaymentGatewaySettingsEntity> findByProvider(String provider);
}
