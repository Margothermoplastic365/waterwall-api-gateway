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
@Table(name = "ai_plans", schema = "gateway")
public class AiPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "token_budget_daily")
    private Long tokenBudgetDaily;

    @Column(name = "token_budget_monthly")
    private Long tokenBudgetMonthly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_models", columnDefinition = "jsonb")
    private String allowedModels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_tools", columnDefinition = "jsonb")
    private String allowedTools;

    @Column(name = "price_per_1k_tokens", precision = 10, scale = 6)
    private BigDecimal pricePer1kTokens;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
