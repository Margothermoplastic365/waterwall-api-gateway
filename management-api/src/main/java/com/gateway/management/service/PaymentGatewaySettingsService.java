package com.gateway.management.service;

import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import com.gateway.management.repository.PaymentGatewaySettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewaySettingsService {

    private final PaymentGatewaySettingsRepository repository;

    @Transactional(readOnly = true)
    public List<PaymentGatewaySettingsEntity> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public PaymentGatewaySettingsEntity getByProvider(String provider) {
        return repository.findByProvider(provider)
                .orElseThrow(() -> new EntityNotFoundException("Payment gateway not found: " + provider));
    }

    @Transactional(readOnly = true)
    public PaymentGatewaySettingsEntity getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment gateway settings not found: " + id));
    }

    @Transactional
    public PaymentGatewaySettingsEntity create(PaymentGatewaySettingsEntity entity) {
        log.info("Creating payment gateway settings for provider: {}", entity.getProvider());
        return repository.save(entity);
    }

    @Transactional
    public PaymentGatewaySettingsEntity update(UUID id, PaymentGatewaySettingsEntity update) {
        PaymentGatewaySettingsEntity existing = getById(id);

        if (update.getDisplayName() != null) existing.setDisplayName(update.getDisplayName());
        if (update.getEnabled() != null) existing.setEnabled(update.getEnabled());
        if (update.getEnvironment() != null) existing.setEnvironment(update.getEnvironment());
        if (update.getSecretKey() != null) existing.setSecretKey(update.getSecretKey());
        if (update.getPublicKey() != null) existing.setPublicKey(update.getPublicKey());
        if (update.getBaseUrl() != null) existing.setBaseUrl(update.getBaseUrl());
        if (update.getCallbackUrl() != null) existing.setCallbackUrl(update.getCallbackUrl());
        if (update.getWebhookUrl() != null) existing.setWebhookUrl(update.getWebhookUrl());
        if (update.getSupportedCurrencies() != null) existing.setSupportedCurrencies(update.getSupportedCurrencies());
        if (update.getExtraConfig() != null) existing.setExtraConfig(update.getExtraConfig());

        log.info("Updated payment gateway settings for provider: {}", existing.getProvider());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        PaymentGatewaySettingsEntity entity = getById(id);
        repository.delete(entity);
        log.info("Deleted payment gateway settings for provider: {}", entity.getProvider());
    }

    @Transactional
    public PaymentGatewaySettingsEntity toggleEnabled(UUID id) {
        PaymentGatewaySettingsEntity entity = getById(id);
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        log.info("Toggled payment gateway {} to enabled={}", entity.getProvider(), entity.getEnabled());
        return repository.save(entity);
    }
}
