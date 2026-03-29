package com.gateway.management.service;

import com.gateway.management.entity.AiPlanEntity;
import com.gateway.management.repository.AiPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD service for AI plans (productization layer).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProductService {

    private final AiPlanRepository aiPlanRepository;

    @Transactional(readOnly = true)
    public List<AiPlanEntity> listPlans() {
        return aiPlanRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AiPlanEntity getPlan(UUID id) {
        return aiPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AI plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public AiPlanEntity getPlanByName(String name) {
        return aiPlanRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("AI plan not found: " + name));
    }

    @Transactional
    public AiPlanEntity createPlan(AiPlanEntity plan) {
        log.info("Creating AI plan: {}", plan.getName());
        return aiPlanRepository.save(plan);
    }

    @Transactional
    public AiPlanEntity updatePlan(UUID id, AiPlanEntity updates) {
        AiPlanEntity existing = getPlan(id);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getTokenBudgetDaily() != null) existing.setTokenBudgetDaily(updates.getTokenBudgetDaily());
        if (updates.getTokenBudgetMonthly() != null) existing.setTokenBudgetMonthly(updates.getTokenBudgetMonthly());
        if (updates.getAllowedModels() != null) existing.setAllowedModels(updates.getAllowedModels());
        if (updates.getAllowedTools() != null) existing.setAllowedTools(updates.getAllowedTools());
        if (updates.getPricePer1kTokens() != null) existing.setPricePer1kTokens(updates.getPricePer1kTokens());
        log.info("Updated AI plan: id={}", id);
        return aiPlanRepository.save(existing);
    }

    @Transactional
    public void deletePlan(UUID id) {
        if (!aiPlanRepository.existsById(id)) {
            throw new IllegalArgumentException("AI plan not found: " + id);
        }
        aiPlanRepository.deleteById(id);
        log.info("Deleted AI plan: id={}", id);
    }
}
