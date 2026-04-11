package com.gateway.management.controller;

import com.gateway.management.dto.CreatePlanRequest;
import com.gateway.management.dto.PlanResponse;
import com.gateway.management.entity.CreditNoteEntity;
import com.gateway.management.entity.InvoiceEntity;
import com.gateway.management.entity.WalletEntity;
import com.gateway.management.repository.WalletRepository;
import com.gateway.management.service.MonetizationService;
import com.gateway.management.service.PlanService;
import com.gateway.management.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/monetization")
@RequiredArgsConstructor
public class MonetizationController {

    private final MonetizationService monetizationService;
    private final PlanService planService;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final com.gateway.management.service.LedgerService ledgerService;

    // ── Pricing Plans (delegates to unified PlanService) ──────────────────

    @PostMapping("/pricing-plans")
    public ResponseEntity<PlanResponse> createPricingPlan(@RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request));
    }

    @GetMapping("/pricing-plans")
    public ResponseEntity<List<PlanResponse>> listPricingPlans() {
        return ResponseEntity.ok(planService.listPlans());
    }

    @GetMapping("/pricing-plans/{id}")
    public ResponseEntity<PlanResponse> getPricingPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getPlan(id));
    }

    @PutMapping("/pricing-plans/{id}")
    public ResponseEntity<PlanResponse> updatePricingPlan(@PathVariable UUID id,
                                                           @RequestBody CreatePlanRequest request) {
        return ResponseEntity.ok(planService.updatePlan(id, request));
    }

    @DeleteMapping("/pricing-plans/{id}")
    public ResponseEntity<Void> deletePricingPlan(@PathVariable UUID id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    // ── Invoices ──────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceEntity>> listInvoices(
            @RequestParam(required = false) UUID consumerId) {
        return ResponseEntity.ok(monetizationService.listInvoices(consumerId));
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<InvoiceEntity> generateInvoice(@RequestBody Map<String, String> request) {
        UUID consumerId = UUID.fromString(request.get("consumerId"));
        String period = request.get("period");
        return ResponseEntity.status(HttpStatus.CREATED).body(monetizationService.generateInvoice(consumerId, period));
    }

    // ── Revenue Report ────────────────────────────────────────────────────

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueReport(
            @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(monetizationService.getRevenueReport(period));
    }

    // ── Refunds ──────────────────────────────────────────────────────────

    @PostMapping("/invoices/{id}/refund")
    public ResponseEntity<CreditNoteEntity> refundInvoice(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        BigDecimal amount = request.containsKey("amount")
                ? new BigDecimal(request.get("amount")) : null;
        String reason = request.getOrDefault("reason", "Admin refund");
        return ResponseEntity.ok(monetizationService.refundInvoice(id, amount, reason));
    }

    // ── Wallets (Admin) ──────────────────────────────────────────────────

    @GetMapping("/wallets")
    public ResponseEntity<List<WalletEntity>> listWallets() {
        return ResponseEntity.ok(walletRepository.findAll());
    }

    @GetMapping("/wallets/{id}")
    public ResponseEntity<WalletEntity> getWallet(@PathVariable UUID id) {
        return ResponseEntity.ok(walletRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Wallet not found: " + id)));
    }

    @GetMapping("/wallets/{consumerId}/statement")
    public ResponseEntity<Map<String, Object>> getWalletStatement(
            @PathVariable UUID consumerId,
            @RequestParam(required = false) String period) {
        WalletEntity wallet = walletRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Wallet not found for consumer: " + consumerId));
        return ResponseEntity.ok(ledgerService.getStatement(wallet.getId(), period));
    }

    @GetMapping("/wallets/{consumerId}/ledger")
    public ResponseEntity<java.util.List<com.gateway.management.entity.LedgerEntryEntity>> getWalletLedger(
            @PathVariable UUID consumerId) {
        WalletEntity wallet = walletRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Wallet not found for consumer: " + consumerId));
        String period = java.time.YearMonth.now().toString();
        return ResponseEntity.ok(ledgerService.getStatement(wallet.getId(), period).get("transactions") != null
                ? (java.util.List<com.gateway.management.entity.LedgerEntryEntity>) ledgerService.getStatement(wallet.getId(), period).get("transactions")
                : java.util.List.of());
    }

    @PostMapping("/wallets/{consumerId}/credit")
    public ResponseEntity<WalletEntity> creditWallet(
            @PathVariable UUID consumerId,
            @RequestBody Map<String, String> request) {
        BigDecimal amount = new BigDecimal(request.get("amount"));
        String description = request.getOrDefault("description", "Admin credit");
        String reference = "ADMIN-CREDIT-" + System.currentTimeMillis();
        return ResponseEntity.ok(walletService.topUp(consumerId, amount, reference, description));
    }
}
