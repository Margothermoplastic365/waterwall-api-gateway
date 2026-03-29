package com.gateway.runtime.controller;

import com.gateway.runtime.dto.FaultConfig;
import com.gateway.runtime.dto.TraceRequest;
import com.gateway.runtime.dto.TraceResult;
import com.gateway.runtime.service.ChaosService;
import com.gateway.runtime.service.RequestTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/gateway/debug")
@RequiredArgsConstructor
public class DebugController {

    private final RequestTraceService requestTraceService;
    private final ChaosService chaosService;

    // ── Trace endpoints ───────────────────────────────────────────────

    @PostMapping("/trace")
    public ResponseEntity<TraceResult> trace(@RequestBody TraceRequest request) {
        TraceResult result = requestTraceService.traceRequest(
                request.getPath(), request.getMethod(),
                request.getHeaders(), request.getBody());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<TraceResult> getTrace(@PathVariable String traceId) {
        TraceResult result = requestTraceService.getTrace(traceId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    // ── Chaos endpoints ───────────────────────────────────────────────

    @PostMapping("/chaos/{apiId}")
    public ResponseEntity<FaultConfig> enableChaos(@PathVariable UUID apiId,
                                                    @RequestBody FaultConfig config) {
        chaosService.enableFaultInjection(apiId, config);
        return ResponseEntity.ok(config);
    }

    @DeleteMapping("/chaos/{apiId}")
    public ResponseEntity<Void> disableChaos(@PathVariable UUID apiId) {
        chaosService.disableFaultInjection(apiId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chaos/{apiId}")
    public ResponseEntity<FaultConfig> getChaosConfig(@PathVariable UUID apiId) {
        FaultConfig config = chaosService.getFaultConfig(apiId);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }
}
