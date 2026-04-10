package com.gateway.management.repository;

import com.gateway.management.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    Optional<WalletEntity> findByConsumerId(UUID consumerId);

    @Query("SELECT w FROM WalletEntity w WHERE w.autoTopUpEnabled = true AND w.balance < w.autoTopUpThreshold")
    List<WalletEntity> findWalletsNeedingTopUp();
}
