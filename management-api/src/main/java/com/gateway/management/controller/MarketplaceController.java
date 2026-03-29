package com.gateway.management.controller;

import com.gateway.management.entity.MarketplacePluginEntity;
import com.gateway.management.service.MarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/plugins")
    public ResponseEntity<List<MarketplacePluginEntity>> listPlugins(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(marketplaceService.listPlugins(type, search));
    }

    @PostMapping("/plugins")
    public ResponseEntity<MarketplacePluginEntity> publishPlugin(@RequestBody MarketplacePluginEntity request) {
        MarketplacePluginEntity result = marketplaceService.publishPlugin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/plugins/{id}")
    public ResponseEntity<MarketplacePluginEntity> getPlugin(@PathVariable UUID id) {
        return ResponseEntity.ok(marketplaceService.getPlugin(id));
    }

    @PostMapping("/plugins/{id}/install")
    public ResponseEntity<MarketplacePluginEntity> installPlugin(@PathVariable UUID id) {
        return ResponseEntity.ok(marketplaceService.installPlugin(id));
    }

    @PostMapping("/plugins/{id}/rate")
    public ResponseEntity<MarketplacePluginEntity> ratePlugin(@PathVariable UUID id,
                                                               @RequestBody Map<String, Integer> request) {
        int rating = request.getOrDefault("rating", 5);
        return ResponseEntity.ok(marketplaceService.ratePlugin(id, rating));
    }
}
