package com.gateway.management.service;

import com.gateway.management.entity.WalletEntity;
import com.gateway.management.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPeriodCloseScheduler {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Runs on the 1st of each month at 00:01.
     * Closes the previous period for all wallets by inserting
     * Balance C/D and Balance B/F entries.
     */
    @Scheduled(cron = "0 1 0 1 * *")
    @Transactional
    public void closeAllPeriods() {
        String previousPeriod = YearMonth.now().minusMonths(1).toString();
        log.info("Starting ledger period close for period={}", previousPeriod);

        List<WalletEntity> wallets = walletRepository.findAll();
        int closed = 0;

        for (WalletEntity wallet : wallets) {
            try {
                String walletPeriod = wallet.getCurrentPeriod();
                // Only close if the wallet's current period is the previous period or earlier
                if (walletPeriod == null || walletPeriod.compareTo(YearMonth.now().toString()) < 0) {
                    String periodToClose = walletPeriod != null ? walletPeriod : previousPeriod;
                    ledgerService.closePeriod(wallet.getId(), periodToClose);
                    closed++;
                }
            } catch (Exception e) {
                log.error("Failed to close period for wallet={}: {}", wallet.getId(), e.getMessage());
            }
        }

        log.info("Ledger period close complete: {} wallets closed for period={}", closed, previousPeriod);
    }
}
