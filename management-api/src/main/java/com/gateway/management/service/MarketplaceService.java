package com.gateway.management.service;

import com.gateway.management.entity.MarketplacePluginEntity;
import com.gateway.management.repository.MarketplacePluginRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final MarketplacePluginRepository pluginRepository;

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public MarketplacePluginEntity publishPlugin(MarketplacePluginEntity entity) {
        if (entity.getRating() == null) entity.setRating(BigDecimal.ZERO);
        if (entity.getReviewCount() == null) entity.setReviewCount(0);
        if (entity.getCertified() == null) entity.setCertified(false);
        if (entity.getInstalledCount() == null) entity.setInstalledCount(0);
        entity = pluginRepository.save(entity);
        log.info("Published plugin: id={} name={} type={}", entity.getId(), entity.getName(), entity.getType());
        return entity;
    }

    public List<MarketplacePluginEntity> listPlugins(String type, String search) {
        if (type != null && !type.isBlank()) {
            return pluginRepository.findByType(type.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            return pluginRepository.findByNameContainingIgnoreCase(search);
        }
        return pluginRepository.findAll();
    }

    public MarketplacePluginEntity getPlugin(UUID id) {
        return pluginRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plugin not found: " + id));
    }

    // ── Install ───────────────────────────────────────────────────────────

    @Transactional
    public MarketplacePluginEntity installPlugin(UUID pluginId) {
        MarketplacePluginEntity plugin = getPlugin(pluginId);
        plugin.setInstalledCount(plugin.getInstalledCount() + 1);
        plugin = pluginRepository.save(plugin);
        log.info("Plugin installed: id={} name={} totalInstalls={}", pluginId, plugin.getName(), plugin.getInstalledCount());
        return plugin;
    }

    // ── Rate ──────────────────────────────────────────────────────────────

    @Transactional
    public MarketplacePluginEntity ratePlugin(UUID pluginId, int rating) {
        MarketplacePluginEntity plugin = getPlugin(pluginId);

        // Simple rolling average
        int currentCount = plugin.getReviewCount();
        BigDecimal currentRating = plugin.getRating();
        BigDecimal newRating = currentRating
                .multiply(BigDecimal.valueOf(currentCount))
                .add(BigDecimal.valueOf(rating))
                .divide(BigDecimal.valueOf(currentCount + 1), 2, RoundingMode.HALF_UP);

        plugin.setRating(newRating);
        plugin.setReviewCount(currentCount + 1);
        plugin = pluginRepository.save(plugin);
        log.info("Plugin rated: id={} newRating={} reviewCount={}", pluginId, newRating, plugin.getReviewCount());
        return plugin;
    }
}
