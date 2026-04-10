package com.gateway.management.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.management.dto.PaymentInitResponse;
import com.gateway.management.entity.WalletEntity;
import com.gateway.management.entity.WalletTransactionEntity;
import com.gateway.management.service.WalletService;
import com.gateway.management.service.payment.PaymentProvider;
import com.gateway.management.service.payment.PaymentProviderFactory;
import com.gateway.management.service.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/consumer/wallet")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WalletController {

    private final WalletService walletService;
    private final PaymentProviderFactory paymentProviderFactory;

    @GetMapping
    public ResponseEntity<WalletEntity> getWallet() {
        return ResponseEntity.ok(walletService.getWallet());
    }

    @PostMapping("/top-up")
    public ResponseEntity<PaymentInitResponse> topUp(@RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String providerName = request.get("provider") != null ? request.get("provider").toString() : null;
        String email = SecurityContextHelper.getCurrentEmail();
        String userId = SecurityContextHelper.getCurrentUserId();

        String reference = "TOPUP-" + userId.substring(0, 8) + "-" + System.currentTimeMillis();
        WalletEntity wallet = walletService.getWallet();

        PaymentProvider provider = providerName != null
                ? paymentProviderFactory.getProvider(providerName)
                : paymentProviderFactory.getActiveProvider();

        PaymentResult.InitResult result = provider.initializePayment(
                email, amount, wallet.getCurrency(), reference, null);

        return ResponseEntity.ok(new PaymentInitResponse(
                result.getAuthorizationUrl(), reference, result.getAccessCode()));
    }

    @GetMapping("/top-up/verify")
    public ResponseEntity<WalletEntity> verifyTopUp(
            @RequestParam String reference,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String provider) {
        String userId = SecurityContextHelper.getCurrentUserId();

        PaymentProvider paymentProvider = provider != null
                ? paymentProviderFactory.getProvider(provider)
                : paymentProviderFactory.getActiveProvider();

        PaymentResult.VerifyResult result = paymentProvider.verifyPayment(reference);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Top-up payment not successful. Status: " + result.getStatus());
        }

        WalletEntity wallet = walletService.topUp(
                UUID.fromString(userId), amount, reference, "Wallet top-up via " + paymentProvider.getProviderName());

        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransactionEntity>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactions(PageRequest.of(page, size)));
    }

    @PutMapping("/settings")
    public ResponseEntity<WalletEntity> updateSettings(@RequestBody Map<String, Object> request) {
        String userId = SecurityContextHelper.getCurrentUserId();

        Boolean autoTopUpEnabled = request.containsKey("autoTopUpEnabled")
                ? Boolean.parseBoolean(request.get("autoTopUpEnabled").toString()) : null;
        BigDecimal autoTopUpThreshold = request.containsKey("autoTopUpThreshold")
                ? new BigDecimal(request.get("autoTopUpThreshold").toString()) : null;
        BigDecimal autoTopUpAmount = request.containsKey("autoTopUpAmount")
                ? new BigDecimal(request.get("autoTopUpAmount").toString()) : null;
        BigDecimal lowBalanceThreshold = request.containsKey("lowBalanceThreshold")
                ? new BigDecimal(request.get("lowBalanceThreshold").toString()) : null;

        WalletEntity wallet = walletService.updateSettings(
                UUID.fromString(userId), autoTopUpEnabled, autoTopUpThreshold,
                autoTopUpAmount, lowBalanceThreshold);

        return ResponseEntity.ok(wallet);
    }
}
