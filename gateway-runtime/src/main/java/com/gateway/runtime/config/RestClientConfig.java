package com.gateway.runtime.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient} used by {@link com.gateway.runtime.controller.ProxyController}
 * for upstream HTTP calls.
 *
 * <p>Timeouts and pool sizes are configurable via {@code application.yml}
 * under {@code gateway.runtime.proxy.*}.</p>
 */
@Configuration
public class RestClientConfig {

    @Value("${gateway.runtime.proxy.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${gateway.runtime.proxy.read-timeout:60000}")
    private int readTimeout;

    @Bean
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeout))
                .withReadTimeout(Duration.ofMillis(readTimeout));

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
