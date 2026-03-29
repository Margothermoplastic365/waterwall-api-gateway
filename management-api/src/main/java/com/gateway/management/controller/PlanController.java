package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.CreatePlanRequest;
import com.gateway.management.dto.PlanResponse;
import com.gateway.management.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @RequiresPermission("plan:create")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse response = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listPlans() {
        return ResponseEntity.ok(planService.listPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getPlan(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission("plan:update")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable UUID id,
                                                    @Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.ok(planService.updatePlan(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("plan:delete")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}
