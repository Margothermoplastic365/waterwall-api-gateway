package com.gateway.management.controller;

import com.gateway.management.entity.RegionEntity;
import com.gateway.management.service.MultiRegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final MultiRegionService multiRegionService;

    @PostMapping
    public ResponseEntity<RegionEntity> createRegion(@RequestBody RegionEntity request) {
        RegionEntity result = multiRegionService.createRegion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<RegionEntity>> listRegions() {
        return ResponseEntity.ok(multiRegionService.listRegions());
    }

    @PostMapping("/deploy")
    public ResponseEntity<Map<String, Object>> deployToRegion(@RequestBody Map<String, String> request) {
        UUID apiId = UUID.fromString(request.get("apiId"));
        String regionSlug = request.get("regionSlug");
        return ResponseEntity.ok(multiRegionService.deployToRegion(apiId, regionSlug));
    }

    @GetMapping("/geo-routing")
    public ResponseEntity<List<Map<String, Object>>> getGeoRoutingConfig() {
        return ResponseEntity.ok(multiRegionService.getGeoRoutingConfig());
    }

    @GetMapping("/data-residency-zones")
    public ResponseEntity<List<String>> getDataResidencyZones() {
        return ResponseEntity.ok(multiRegionService.getDataResidencyZones());
    }
}
