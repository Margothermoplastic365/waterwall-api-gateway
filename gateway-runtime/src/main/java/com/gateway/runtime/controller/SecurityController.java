package com.gateway.runtime.controller;

import com.gateway.runtime.security.AbuseDetectionService;
import com.gateway.runtime.security.BotDetectionFilter;
import com.gateway.runtime.security.SecurityPostureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes security-related endpoints for monitoring bot detection,
 * abuse risk scores, and overall security posture.
 */
@RestController
@RequestMapping("/v1/gateway/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityPostureService securityPostureService;
    private final BotDetectionFilter botDetectionFilter;
    private final AbuseDetectionService abuseDetectionService;

    @GetMapping("/posture")
    public ResponseEntity<SecurityPostureService.SecurityPostureReport> getSecurityPosture() {
        return ResponseEntity.ok(securityPostureService.audit());
    }

    @GetMapping("/bots/stats")
    public ResponseEntity<Map<String, Object>> getBotStats() {
        return ResponseEntity.ok(botDetectionFilter.getStats());
    }

    @GetMapping("/abuse/risk-scores")
    public ResponseEntity<Map<String, Integer>> getAbuseRiskScores() {
        return ResponseEntity.ok(abuseDetectionService.getAllRiskScores());
    }
}
