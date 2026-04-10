package com.gateway.management.repository;

import com.gateway.management.entity.WalletTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, UUID> {

    Page<WalletTransactionEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
