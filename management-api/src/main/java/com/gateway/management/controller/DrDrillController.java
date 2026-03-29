package com.gateway.management.controller;

import com.gateway.management.service.DrDrillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/dr")
@RequiredArgsConstructor
public class DrDrillController {

    private final DrDrillService drDrillService;

    @PostMapping("/drill")
    public ResponseEntity<DrDrillService.DrillResult> runDrill(@RequestBody Map<String, String> request) {
        String scenario = request.getOrDefault("scenario", "DB_FAILOVER");
        return ResponseEntity.ok(drDrillService.runDrill(scenario));
    }

    @GetMapping("/drills")
    public ResponseEntity<List<DrDrillService.DrillResult>> listDrillResults() {
        return ResponseEntity.ok(drDrillService.listDrillResults());
    }
}
