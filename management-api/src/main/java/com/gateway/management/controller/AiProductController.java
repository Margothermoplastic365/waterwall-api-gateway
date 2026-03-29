package com.gateway.management.controller;

import com.gateway.management.entity.AiPlanEntity;
import com.gateway.management.service.AiProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing AI plans (productization).
 */
@RestController
@RequestMapping("/v1/ai/plans")
@RequiredArgsConstructor
public class AiProductController {

    private final AiProductService aiProductService;

    @GetMapping
    public ResponseEntity<List<AiPlanEntity>> listPlans() {
        return ResponseEntity.ok(aiProductService.listPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiPlanEntity> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(aiProductService.getPlan(id));
    }

    @PostMapping
    public ResponseEntity<AiPlanEntity> createPlan(@RequestBody AiPlanEntity plan) {
        AiPlanEntity created = aiProductService.createPlan(plan);
        return ResponseEntity
                .created(URI.create("/v1/ai/plans/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiPlanEntity> updatePlan(
            @PathVariable UUID id,
            @RequestBody AiPlanEntity plan) {
        return ResponseEntity.ok(aiProductService.updatePlan(id, plan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        aiProductService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}
