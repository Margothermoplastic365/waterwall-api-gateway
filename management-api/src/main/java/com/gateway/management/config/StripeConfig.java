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
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {

    private String secretKey;
    private String publishableKey;
    private String webhookSecret;
    private String baseUrl = "https://api.stripe.com";

    public PaymentGatewaySettingsEntity resolveSettings(PaymentGatewaySettingsRepository repository) {
        Optional<PaymentGatewaySettingsEntity> dbSettings = repository.findByProvider("stripe");
        if (dbSettings.isPresent()) {
            return dbSettings.get();
        }
        return PaymentGatewaySettingsEntity.builder()
                .provider("stripe")
                .displayName("Stripe")
                .enabled(false)
                .secretKey(secretKey)
                .publicKey(publishableKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("stripeRestClient")
    public RestClient stripeRestClient() {
        return RestClient.builder().build();
    }
}
