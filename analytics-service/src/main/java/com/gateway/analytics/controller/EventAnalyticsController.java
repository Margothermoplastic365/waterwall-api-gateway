package com.gateway.analytics.controller;

import com.gateway.analytics.service.EventAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/analytics/events")
@RequiredArgsConstructor
public class EventAnalyticsController {

    private final EventAnalyticsService eventAnalyticsService;

    /**
     * GET /throughput?topic=xxx — events per second / total count for a topic.
     * If topic is omitted, returns all topics.
     */
    @GetMapping("/throughput")
    public ResponseEntity<?> getThroughput(@RequestParam(required = false) String topic) {
        if (topic != null && !topic.isBlank()) {
            return ResponseEntity.ok(eventAnalyticsService.getThroughput(topic));
        }
        return ResponseEntity.ok(eventAnalyticsService.getAllThroughput());
    }

    /**
     * GET /consumers — consumer lag per subscription.
     */
    @GetMapping("/consumers")
    public ResponseEntity<List<Map<String, Object>>> getConsumerLag() {
        return ResponseEntity.ok(eventAnalyticsService.getConsumerLag());
    }

    /**
     * GET /latency — processing latency statistics per topic.
     */
    @GetMapping("/latency")
    public ResponseEntity<List<Map<String, Object>>> getLatencyStats() {
        return ResponseEntity.ok(eventAnalyticsService.getLatencyStats());
    }
}
