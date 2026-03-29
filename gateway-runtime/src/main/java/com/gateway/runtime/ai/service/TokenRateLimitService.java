package com.gateway.runtime.ai.service;

import com.gateway.runtime.ai.model.TokenBudgetStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Token-based rate limiting service that enforces per-consumer token budgets
 * (daily/monthly) and records usage in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRateLimitService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Check whether the consumer has sufficient token budget for the estimated request.
     *
     * @param consumerId      the consumer/API-key identifier
     * @param estimatedTokens estimated tokens for this request
     * @return true if the budget allows the request, false if budget exceeded
     */
    public boolean checkTokenBudget(String consumerId, int estimatedTokens) {
        TokenBudgetStatus status = getBudgetStatus(consumerId);
        if (status == null) {
            // No budget configured — allow by default
            return true;
        }
        if (status.getDailyLimit() > 0 && (status.getDailyUsed() + estimatedTokens) > status.getDailyLimit()) {
            log.warn("Daily token budget exceeded for consumer {}: used={}, limit={}", consumerId, status.getDailyUsed(), status.getDailyLimit());
            return false;
        }
        if (status.getMonthlyLimit() > 0 && (status.getMonthlyUsed() + estimatedTokens) > status.getMonthlyLimit()) {
            log.warn("Monthly token budget exceeded for consumer {}: used={}, limit={}", consumerId, status.getMonthlyUsed(), status.getMonthlyLimit());
            return false;
        }
        return true;
    }

    /**
     * Record token usage after a successful LLM call.
     */
    public void recordTokenUsage(String consumerId, String model, String provider, int promptTokens, int completionTokens) {
        int totalTokens = promptTokens + completionTokens;
        double estimatedCost = estimateCost(model, promptTokens, completionTokens);

        try {
            jdbcTemplate.update(
                    "INSERT INTO gateway.ai_token_usage (consumer_id, model, provider, prompt_tokens, completion_tokens, total_tokens, estimated_cost_usd, created_at) " +
                            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, now())",
                    consumerId, model, provider, promptTokens, completionTokens, totalTokens, estimatedCost
            );
        } catch (Exception ex) {
            log.error("Failed to record token usage for consumer {}: {}", consumerId, ex.getMessage());
        }
    }

    /**
     * Get the current budget status for a consumer.
     */
    public TokenBudgetStatus getBudgetStatus(String consumerId) {
        try {
            // Get budget limits
            var budgets = jdbcTemplate.queryForList(
                    "SELECT daily_limit, monthly_limit FROM gateway.ai_token_budgets WHERE consumer_id = ?::uuid",
                    consumerId
            );
            if (budgets.isEmpty()) {
                return null;
            }

            long dailyLimit = ((Number) budgets.get(0).getOrDefault("daily_limit", 0L)).longValue();
            long monthlyLimit = ((Number) budgets.get(0).getOrDefault("monthly_limit", 0L)).longValue();

            // Get daily usage
            LocalDate today = LocalDate.now();
            long dailyUsed = getDailyUsage(consumerId, today);

            // Get monthly usage
            LocalDate firstOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
            long monthlyUsed = getMonthlyUsage(consumerId, firstOfMonth);

            double estimatedCost = getEstimatedCost(consumerId, firstOfMonth);

            return TokenBudgetStatus.builder()
                    .consumerId(consumerId)
                    .dailyLimit(dailyLimit)
                    .dailyUsed(dailyUsed)
                    .monthlyLimit(monthlyLimit)
                    .monthlyUsed(monthlyUsed)
                    .estimatedCost(estimatedCost)
                    .build();
        } catch (Exception ex) {
            log.error("Failed to get budget status for consumer {}: {}", consumerId, ex.getMessage());
            return null;
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private long getDailyUsage(String consumerId, LocalDate date) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_tokens), 0) FROM gateway.ai_token_usage " +
                        "WHERE consumer_id = ?::uuid AND created_at >= ?::date AND created_at < (?::date + interval '1 day')",
                Long.class, consumerId, date.toString(), date.toString()
        );
        return result != null ? result : 0L;
    }

    private long getMonthlyUsage(String consumerId, LocalDate firstOfMonth) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_tokens), 0) FROM gateway.ai_token_usage " +
                        "WHERE consumer_id = ?::uuid AND created_at >= ?::date",
                Long.class, consumerId, firstOfMonth.toString()
        );
        return result != null ? result : 0L;
    }

    private double getEstimatedCost(String consumerId, LocalDate firstOfMonth) {
        Double result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(estimated_cost_usd), 0.0) FROM gateway.ai_token_usage " +
                        "WHERE consumer_id = ?::uuid AND created_at >= ?::date",
                Double.class, consumerId, firstOfMonth.toString()
        );
        return result != null ? result : 0.0;
    }

    private double estimateCost(String model, int promptTokens, int completionTokens) {
        // Rough cost estimates per 1M tokens (input/output)
        double inputCostPer1M;
        double outputCostPer1M;
        if (model != null && model.contains("gpt-4")) {
            inputCostPer1M = 2.50;
            outputCostPer1M = 10.00;
        } else if (model != null && model.contains("claude")) {
            inputCostPer1M = 3.00;
            outputCostPer1M = 15.00;
        } else if (model != null && model.contains("deepseek")) {
            inputCostPer1M = 0.14;
            outputCostPer1M = 0.28;
        } else {
            inputCostPer1M = 1.00;
            outputCostPer1M = 2.00;
        }
        return (promptTokens * inputCostPer1M / 1_000_000.0) + (completionTokens * outputCostPer1M / 1_000_000.0);
    }
}
