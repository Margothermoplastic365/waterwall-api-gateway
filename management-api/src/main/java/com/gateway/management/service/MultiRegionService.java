package com.gateway.management.service;

import com.gateway.management.entity.RegionEntity;
import com.gateway.management.repository.RegionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiRegionService {

    private final RegionRepository regionRepository;

    // ── Regions CRUD ──────────────────────────────────────────────────────

    @Transactional
    public RegionEntity createRegion(RegionEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus("ACTIVE");
        }
        entity = regionRepository.save(entity);
        log.info("Created region: id={} slug={}", entity.getId(), entity.getSlug());
        return entity;
    }

    public List<RegionEntity> listRegions() {
        return regionRepository.findAll();
    }

    public RegionEntity getRegion(UUID id) {
        return regionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Region not found: " + id));
    }

    @Transactional
    public RegionEntity updateRegion(UUID id, RegionEntity update) {
        RegionEntity entity = getRegion(id);
        entity.setName(update.getName());
        entity.setSlug(update.getSlug());
        entity.setEndpointUrl(update.getEndpointUrl());
        entity.setDataResidencyZone(update.getDataResidencyZone());
        entity.setStatus(update.getStatus());
        entity = regionRepository.save(entity);
        log.info("Updated region: id={}", id);
        return entity;
    }

    // ── Deploy API to Region ──────────────────────────────────────────────

    public Map<String, Object> deployToRegion(UUID apiId, String regionSlug) {
        RegionEntity region = regionRepository.findBySlug(regionSlug)
                .orElseThrow(() -> new EntityNotFoundException("Region not found: " + regionSlug));

        log.info("Deploying API {} to region {} ({})", apiId, regionSlug, region.getEndpointUrl());

        // Stub deployment
        return Map.of(
                "apiId", apiId,
                "regionSlug", regionSlug,
                "regionEndpoint", region.getEndpointUrl(),
                "status", "DEPLOYED",
                "message", "API deployed to region " + region.getName()
        );
    }

    // ── Geo Routing Config ────────────────────────────────────────────────

    public List<Map<String, Object>> getGeoRoutingConfig() {
        return regionRepository.findByStatus("ACTIVE").stream()
                .map(region -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("regionSlug", region.getSlug());
                    entry.put("regionName", region.getName());
                    entry.put("endpointUrl", region.getEndpointUrl());
                    entry.put("dataResidencyZone", region.getDataResidencyZone());
                    return entry;
                })
                .collect(Collectors.toList());
    }

    // ── Data Residency Zones ──────────────────────────────────────────────

    public List<String> getDataResidencyZones() {
        return regionRepository.findAll().stream()
                .map(RegionEntity::getDataResidencyZone)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
