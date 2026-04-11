package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_entries", schema = "gateway")
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "api_id")
    private UUID apiId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "pricing_model", length = 20)
    private String pricingModel;

    @Column(name = "billing_period", length = 20)
    private String billingPeriod;

    @Column(name = "running_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal runningBalance;

    @Column(name = "related_invoice_id")
    private UUID relatedInvoiceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // Entry type constants
    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String TYPE_BALANCE_BF = "BALANCE_BF";
    public static final String TYPE_BALANCE_CD = "BALANCE_CD";

    // Category constants
    public static final String CAT_TOP_UP = "TOP_UP";
    public static final String CAT_USAGE_CHARGE = "USAGE_CHARGE";
    public static final String CAT_SUBSCRIPTION_FEE = "SUBSCRIPTION_FEE";
    public static final String CAT_REFUND = "REFUND";
    public static final String CAT_ADJUSTMENT = "ADJUSTMENT";
    public static final String CAT_PERIOD_CLOSE = "PERIOD_CLOSE";
    public static final String CAT_PERIOD_OPEN = "PERIOD_OPEN";
}
