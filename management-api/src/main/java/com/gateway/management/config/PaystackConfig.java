package com.gateway.management.config;

import com.gateway.management.entity.PaymentGatewaySettingsEntity;
import com.gateway.management.repository.PaymentGatewaySettingsRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "paystack")
public class PaystackConfig {

    // Fallback values from application.yml
    private String secretKey;
    private String publicKey;
    private String baseUrl;
    private String callbackUrl;

    /**
     * Returns the effective configuration by checking the database first,
     * falling back to application.yml values.
     */
    public PaymentGatewaySettingsEntity resolveSettings(PaymentGatewaySettingsRepository repository) {
        Optional<PaymentGatewaySettingsEntity> dbSettings = repository.findByProvider("paystack");
        if (dbSettings.isPresent()) {
            PaymentGatewaySettingsEntity s = dbSettings.get();
            return s;
        }
        // Build a fallback entity from yml values
        return PaymentGatewaySettingsEntity.builder()
                .provider("paystack")
                .displayName("Paystack")
                .enabled(true)
                .secretKey(secretKey)
                .publicKey(publicKey)
                .baseUrl(baseUrl)
                .callbackUrl(callbackUrl)
                .build();
    }

    @Bean("paystackRestClient")
    public RestClient paystackRestClient() {
        return RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
