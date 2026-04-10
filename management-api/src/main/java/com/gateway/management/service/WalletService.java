package com.gateway.management.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.WalletEntity;
import com.gateway.management.entity.WalletTransactionEntity;
import com.gateway.management.repository.InvoiceRepository;
import com.gateway.management.repository.WalletRepository;
import com.gateway.management.repository.WalletTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewaySettingsService paymentGatewaySettingsService;
    private final EventPublisher eventPublisher;

    @Transactional
    public WalletEntity getOrCreateWallet(UUID consumerId) {
        return walletRepository.findByConsumerId(consumerId)
                .orElseGet(() -> {
                    WalletEntity wallet = WalletEntity.builder()
                            .consumerId(consumerId)
                            .balance(BigDecimal.ZERO)
                            .currency(paymentGatewaySettingsService.getDefaultCurrency())
                            .build();
                    log.info("Created wallet for consumer={}", consumerId);
                    return walletRepository.save(wallet);
                });
    }

    @Transactional(readOnly = true)
    public WalletEntity getWallet() {
        UUID consumerId = resolveConsumerId();
        return getOrCreateWallet(consumerId);
    }

    @Transactional
    public WalletEntity topUp(UUID consumerId, BigDecimal amount, String reference, String description) {
        WalletEntity wallet = getOrCreateWallet(consumerId);
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet = walletRepository.save(wallet);

        WalletTransactionEntity txn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .type("CREDIT")
                .amount(amount)
                .currency(wallet.getCurrency())
                .reference(reference)
                .description(description != null ? description : "Wallet top-up")
                .balanceAfter(wallet.getBalance())
                .build();
        walletTransactionRepository.save(txn);

        log.info("Wallet topped up: consumer={} amount={} newBalance={}", consumerId, amount, wallet.getBalance());
        return wallet;
    }

    @Transactional
    public WalletEntity deduct(UUID consumerId, BigDecimal amount, String reference,
                                String description, UUID invoiceId) {
        WalletEntity wallet = getOrCreateWallet(consumerId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient wallet balance. Available: "
                    + wallet.getBalance() + ", Required: " + amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet = walletRepository.save(wallet);

        WalletTransactionEntity txn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .type("DEBIT")
                .amount(amount)
                .currency(wallet.getCurrency())
                .reference(reference)
                .description(description != null ? description : "Payment deduction")
                .balanceAfter(wallet.getBalance())
                .relatedInvoiceId(invoiceId)
                .build();
        walletTransactionRepository.save(txn);

        log.info("Wallet debited: consumer={} amount={} newBalance={}", consumerId, amount, wallet.getBalance());

        // Check low balance threshold and notify
        if (wallet.getLowBalanceThreshold() != null
                && wallet.getBalance().compareTo(wallet.getLowBalanceThreshold()) < 0) {
            publishLowBalanceAlert(consumerId, wallet.getBalance(), wallet.getLowBalanceThreshold());
        }

        return wallet;
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionEntity> getTransactions(Pageable pageable) {
        UUID consumerId = resolveConsumerId();
        WalletEntity wallet = getOrCreateWallet(consumerId);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
    }

    @Transactional
    public WalletEntity updateSettings(UUID consumerId, Boolean autoTopUpEnabled,
                                        BigDecimal autoTopUpThreshold, BigDecimal autoTopUpAmount,
                                        BigDecimal lowBalanceThreshold) {
        WalletEntity wallet = getOrCreateWallet(consumerId);

        if (autoTopUpEnabled != null) wallet.setAutoTopUpEnabled(autoTopUpEnabled);
        if (autoTopUpThreshold != null) wallet.setAutoTopUpThreshold(autoTopUpThreshold);
        if (autoTopUpAmount != null) wallet.setAutoTopUpAmount(autoTopUpAmount);
        if (lowBalanceThreshold != null) wallet.setLowBalanceThreshold(lowBalanceThreshold);

        wallet = walletRepository.save(wallet);
        log.info("Wallet settings updated: consumer={} autoTopUp={} threshold={} amount={} lowBalance={}",
                consumerId, wallet.getAutoTopUpEnabled(), wallet.getAutoTopUpThreshold(),
                wallet.getAutoTopUpAmount(), wallet.getLowBalanceThreshold());
        return wallet;
    }

    /**
     * Checks if consumer has sufficient wallet balance for the given amount.
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(UUID consumerId, BigDecimal amount) {
        return walletRepository.findByConsumerId(consumerId)
                .map(w -> w.getBalance().compareTo(amount) >= 0)
                .orElse(false);
    }

    /**
     * Gets wallet balance for a consumer, returns ZERO if no wallet exists.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID consumerId) {
        return walletRepository.findByConsumerId(consumerId)
                .map(WalletEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private void publishLowBalanceAlert(UUID consumerId, BigDecimal balance, BigDecimal threshold) {
        log.warn("Low wallet balance: consumer={} balance={} threshold={}", consumerId, balance, threshold);
        BillingSchedulerService.BillingEvent event = BillingSchedulerService.BillingEvent.builder()
                .eventType("wallet.low_balance")
                .actorId("wallet-service")
                .consumerId(consumerId.toString())
                .build();
        eventPublisher.publish(RabbitMQExchanges.PLATFORM_EVENTS, "wallet.low_balance", event);
    }

    private UUID resolveConsumerId() {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated consumer found");
        }
        return UUID.fromString(userId);
    }
}
