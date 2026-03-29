package com.gateway.management.repository;

import com.gateway.management.entity.PricingPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PricingPlanRepository extends JpaRepository<PricingPlanEntity, UUID> {

    List<PricingPlanEntity> findByPricingModel(String pricingModel);
}
