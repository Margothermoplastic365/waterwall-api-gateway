package com.gateway.management.repository;

import com.gateway.management.entity.LedgerEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    Page<LedgerEntryEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    List<LedgerEntryEntity> findByWalletIdAndBillingPeriodOrderByCreatedAtAsc(UUID walletId, String billingPeriod);

    Optional<LedgerEntryEntity> findByReference(String reference);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType IN ('CREDIT', 'BALANCE_BF') THEN e.amount " +
           "WHEN e.entryType = 'DEBIT' THEN -e.amount ELSE 0 END), 0) " +
           "FROM LedgerEntryEntity e WHERE e.walletId = :walletId")
    BigDecimal calculateBalance(@Param("walletId") UUID walletId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType IN ('CREDIT', 'BALANCE_BF') THEN e.amount " +
           "WHEN e.entryType = 'DEBIT' THEN -e.amount ELSE 0 END), 0) " +
           "FROM LedgerEntryEntity e WHERE e.walletId = :walletId AND e.billingPeriod = :period")
    BigDecimal calculateBalanceForPeriod(@Param("walletId") UUID walletId, @Param("period") String period);

    Optional<LedgerEntryEntity> findFirstByWalletIdAndEntryTypeAndBillingPeriodOrderByCreatedAtDesc(
            UUID walletId, String entryType, String billingPeriod);

    Optional<LedgerEntryEntity> findFirstByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<LedgerEntryEntity> findByWalletIdAndEntryTypeOrderByCreatedAtDesc(UUID walletId, String entryType);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntryEntity e " +
           "WHERE e.walletId = :walletId AND e.entryType = 'DEBIT' AND e.billingPeriod = :period")
    BigDecimal totalDebitsForPeriod(@Param("walletId") UUID walletId, @Param("period") String period);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntryEntity e " +
           "WHERE e.walletId = :walletId AND e.entryType = 'CREDIT' AND e.billingPeriod = :period")
    BigDecimal totalCreditsForPeriod(@Param("walletId") UUID walletId, @Param("period") String period);
}
