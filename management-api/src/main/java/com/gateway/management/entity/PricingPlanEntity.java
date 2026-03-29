package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pricing_plans", schema = "gateway")
public class PricingPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "pricing_model", nullable = false, length = 50)
    private String pricingModel;

    @Column(name = "price_amount", precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "billing_period", length = 50)
    private String billingPeriod;

    @Column(name = "included_requests")
    private Long includedRequests;

    @Column(name = "overage_rate", precision = 10, scale = 6)
    private BigDecimal overageRate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
