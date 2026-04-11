package com.gateway.management.service;

import com.gateway.management.entity.WalletEntity;
import com.gateway.management.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerReconciliationScheduler {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Runs daily at 4:00 AM.
     * Compares cached wallet balance with ledger-calculated balance
     * and corrects any drift.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void reconcileAll() {
        log.info("Starting daily ledger reconciliation");

        List<WalletEntity> wallets = walletRepository.findAll();
        int reconciled = 0;
        int drifted = 0;

        for (WalletEntity wallet : wallets) {
            try {
                BigDecimal drift = ledgerService.reconcile(wallet.getId());
                reconciled++;
                if (drift.signum() != 0) {
                    drifted++;
                    log.warn("Drift detected for wallet={}: {}", wallet.getId(), drift);
                }
            } catch (Exception e) {
                log.error("Reconciliation failed for wallet={}: {}", wallet.getId(), e.getMessage());
            }
        }

        log.info("Ledger reconciliation complete: {} wallets checked, {} had drift", reconciled, drifted);
    }
}
