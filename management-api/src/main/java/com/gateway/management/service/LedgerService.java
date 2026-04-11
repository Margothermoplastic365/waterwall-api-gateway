package com.gateway.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.entity.LedgerEntryEntity;
import com.gateway.management.entity.WalletEntity;
import com.gateway.management.repository.LedgerEntryRepository;
import com.gateway.management.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final PaymentGatewaySettingsService paymentGatewaySettingsService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ── Credit (money in) ────────────────────────────────────────────────

    @Transactional
    public LedgerEntryEntity credit(UUID walletId, BigDecimal amount, String category,
                                     String reference, String description, Map<String, Object> metadata) {
        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        // Idempotency check
        if (reference != null) {
            Optional<LedgerEntryEntity> existing = ledgerEntryRepository.findByReference(reference);
            if (existing.isPresent()) {
                log.info("Ledger credit already exists for reference={}, skipping", reference);
                return existing.get();
            }
        }

        BigDecimal currentBalance = getBalanceFromLedger(walletId);
        BigDecimal newBalance = currentBalance.add(amount);
        String period = currentPeriod();

        LedgerEntryEntity entry = LedgerEntryEntity.builder()
                .walletId(walletId)
                .entryType(LedgerEntryEntity.TYPE_CREDIT)
                .category(category)
                .amount(amount)
                .currency(wallet.getCurrency())
                .reference(reference)
                .description(description)
                .billingPeriod(period)
                .runningBalance(newBalance)
                .metadata(serializeMetadata(metadata))
                .build();

        entry = ledgerEntryRepository.save(entry);

        // Update cached balance
        wallet.setBalance(newBalance);
        wallet.setCurrentPeriod(period);
        walletRepository.save(wallet);

        log.info("Ledger CREDIT: wallet={} amount={} balance={} category={} ref={}",
                walletId, amount, newBalance, category, reference);
        return entry;
    }

    // ── Debit (money out) ────────────────────────────────────────────────

    @Transactional
    public LedgerEntryEntity debit(UUID walletId, BigDecimal amount, String category,
                                    String reference, String description,
                                    UUID apiId, UUID planId, String pricingModel,
                                    Map<String, Object> metadata) {
        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        // Idempotency check
        if (reference != null) {
            Optional<LedgerEntryEntity> existing = ledgerEntryRepository.findByReference(reference);
            if (existing.isPresent()) {
                log.info("Ledger debit already exists for reference={}, skipping", reference);
                return existing.get();
            }
        }

        BigDecimal currentBalance = getBalanceFromLedger(walletId);

        if (currentBalance.compareTo(amount) < 0) {
            // Check low balance alert
            if (wallet.getLowBalanceThreshold() != null) {
                publishLowBalanceAlert(wallet);
            }
            throw new IllegalStateException(
                    "Insufficient wallet balance. Available: " + currentBalance + ", Required: " + amount);
        }

        BigDecimal newBalance = currentBalance.subtract(amount);
        String period = currentPeriod();

        LedgerEntryEntity entry = LedgerEntryEntity.builder()
                .walletId(walletId)
                .entryType(LedgerEntryEntity.TYPE_DEBIT)
                .category(category)
                .amount(amount)
                .currency(wallet.getCurrency())
                .reference(reference)
                .description(description)
                .apiId(apiId)
                .planId(planId)
                .pricingModel(pricingModel)
                .billingPeriod(period)
                .runningBalance(newBalance)
                .metadata(serializeMetadata(metadata))
                .build();

        entry = ledgerEntryRepository.save(entry);

        // Update cached balance
        wallet.setBalance(newBalance);
        wallet.setCurrentPeriod(period);
        walletRepository.save(wallet);

        // Check low balance threshold
        if (wallet.getLowBalanceThreshold() != null
                && newBalance.compareTo(wallet.getLowBalanceThreshold()) < 0) {
            publishLowBalanceAlert(wallet);
        }

        log.info("Ledger DEBIT: wallet={} amount={} balance={} category={} api={} model={} ref={}",
                walletId, amount, newBalance, category, apiId, pricingModel, reference);
        return entry;
    }

    // ── Balance ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BigDecimal getBalanceFromLedger(UUID walletId) {
        return ledgerEntryRepository.calculateBalance(walletId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCachedBalance(UUID walletId) {
        return walletRepository.findById(walletId)
                .map(WalletEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    // ── Statement ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getStatement(UUID walletId, String period) {
        if (period == null) period = currentPeriod();

        List<LedgerEntryEntity> entries = ledgerEntryRepository
                .findByWalletIdAndBillingPeriodOrderByCreatedAtAsc(walletId, period);

        // Find Balance B/F for this period
        BigDecimal balanceBF = BigDecimal.ZERO;
        Optional<LedgerEntryEntity> bfEntry = entries.stream()
                .filter(e -> LedgerEntryEntity.TYPE_BALANCE_BF.equals(e.getEntryType()))
                .findFirst();
        if (bfEntry.isPresent()) {
            balanceBF = bfEntry.get().getAmount();
        }

        // Find Balance C/D if period is closed
        BigDecimal balanceCD = null;
        Optional<LedgerEntryEntity> cdEntry = entries.stream()
                .filter(e -> LedgerEntryEntity.TYPE_BALANCE_CD.equals(e.getEntryType()))
                .findFirst();
        if (cdEntry.isPresent()) {
            balanceCD = cdEntry.get().getAmount();
        }

        // Calculate totals
        BigDecimal totalCredits = entries.stream()
                .filter(e -> LedgerEntryEntity.TYPE_CREDIT.equals(e.getEntryType()))
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebits = entries.stream()
                .filter(e -> LedgerEntryEntity.TYPE_DEBIT.equals(e.getEntryType()))
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If period not closed, calculate current balance
        if (balanceCD == null) {
            balanceCD = balanceBF.add(totalCredits).subtract(totalDebits);
        }

        // Filter out B/F and C/D entries for the transaction list
        List<LedgerEntryEntity> transactions = entries.stream()
                .filter(e -> !LedgerEntryEntity.TYPE_BALANCE_BF.equals(e.getEntryType())
                        && !LedgerEntryEntity.TYPE_BALANCE_CD.equals(e.getEntryType()))
                .toList();

        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("period", period);
        statement.put("balanceBroughtForward", balanceBF);
        statement.put("totalCredits", totalCredits);
        statement.put("totalDebits", totalDebits);
        statement.put("balanceCarriedDown", balanceCD);
        statement.put("transactionCount", transactions.size());
        statement.put("transactions", transactions);
        statement.put("closed", cdEntry.isPresent());

        return statement;
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryEntity> getTransactions(UUID walletId, Pageable pageable) {
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    // ── Period Close ─────────────────────────────────────────────────────

    @Transactional
    public void closePeriod(UUID walletId, String period) {
        // Check if already closed
        Optional<LedgerEntryEntity> existingCD = ledgerEntryRepository
                .findFirstByWalletIdAndEntryTypeAndBillingPeriodOrderByCreatedAtDesc(
                        walletId, LedgerEntryEntity.TYPE_BALANCE_CD, period);
        if (existingCD.isPresent()) {
            log.info("Period {} already closed for wallet={}", period, walletId);
            return;
        }

        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        BigDecimal balance = getBalanceFromLedger(walletId);
        String nextPeriod = nextPeriod(period);

        // Insert Balance C/D for closing period
        LedgerEntryEntity cdEntry = LedgerEntryEntity.builder()
                .walletId(walletId)
                .entryType(LedgerEntryEntity.TYPE_BALANCE_CD)
                .category(LedgerEntryEntity.CAT_PERIOD_CLOSE)
                .amount(balance)
                .currency(wallet.getCurrency())
                .reference("CD-" + walletId.toString().substring(0, 8) + "-" + period)
                .description("Balance carried down for " + period)
                .billingPeriod(period)
                .runningBalance(balance)
                .build();
        ledgerEntryRepository.save(cdEntry);

        // Insert Balance B/F for next period
        LedgerEntryEntity bfEntry = LedgerEntryEntity.builder()
                .walletId(walletId)
                .entryType(LedgerEntryEntity.TYPE_BALANCE_BF)
                .category(LedgerEntryEntity.CAT_PERIOD_OPEN)
                .amount(balance)
                .currency(wallet.getCurrency())
                .reference("BF-" + walletId.toString().substring(0, 8) + "-" + nextPeriod)
                .description("Balance brought forward from " + period)
                .billingPeriod(nextPeriod)
                .runningBalance(balance)
                .build();
        ledgerEntryRepository.save(bfEntry);

        // Update wallet period
        wallet.setCurrentPeriod(nextPeriod);
        wallet.setBalance(balance);
        walletRepository.save(wallet);

        log.info("Period closed: wallet={} period={} balance={} nextPeriod={}",
                walletId, period, balance, nextPeriod);
    }

    // ── Reconciliation ───────────────────────────────────────────────────

    @Transactional
    public BigDecimal reconcile(UUID walletId) {
        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        BigDecimal ledgerBalance = getBalanceFromLedger(walletId);
        BigDecimal cachedBalance = wallet.getBalance();

        if (ledgerBalance.compareTo(cachedBalance) != 0) {
            BigDecimal drift = ledgerBalance.subtract(cachedBalance);
            log.warn("RECONCILIATION DRIFT: wallet={} cached={} ledger={} drift={}",
                    walletId, cachedBalance, ledgerBalance, drift);

            // Correct the cached balance
            wallet.setBalance(ledgerBalance);
            wallet.setLastReconciledAt(Instant.now());
            walletRepository.save(wallet);

            return drift;
        }

        wallet.setLastReconciledAt(Instant.now());
        walletRepository.save(wallet);
        log.debug("Reconciliation OK: wallet={} balance={}", walletId, ledgerBalance);
        return BigDecimal.ZERO;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String currentPeriod() {
        return YearMonth.now().toString();
    }

    private String nextPeriod(String period) {
        return YearMonth.parse(period).plusMonths(1).toString();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize ledger metadata: {}", e.getMessage());
            return null;
        }
    }

    private void publishLowBalanceAlert(WalletEntity wallet) {
        BillingSchedulerService.BillingEvent event = BillingSchedulerService.BillingEvent.builder()
                .eventType("wallet.low_balance")
                .actorId("ledger-service")
                .consumerId(wallet.getConsumerId().toString())
                .build();
        eventPublisher.publish(RabbitMQExchanges.PLATFORM_EVENTS, "wallet.low_balance", event);
    }
}
