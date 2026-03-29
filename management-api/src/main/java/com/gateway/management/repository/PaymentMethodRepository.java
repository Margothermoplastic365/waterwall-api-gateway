package com.gateway.management.repository;

import com.gateway.management.entity.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, UUID> {

    List<PaymentMethodEntity> findByConsumerId(UUID consumerId);
}
