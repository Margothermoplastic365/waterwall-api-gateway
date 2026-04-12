package com.gateway.runtime.config;

import java.time.Duration;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient} used for upstream HTTP calls
 * with connection pooling for high throughput.
 */
@Configuration
public class RestClientConfig {

    @Value("${gateway.runtime.proxy.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${gateway.runtime.proxy.read-timeout:30000}")
    private int readTimeout;

    @Value("${gateway.runtime.proxy.pool.max-total:500}")
    private int poolMaxTotal;

    @Value("${gateway.runtime.proxy.pool.max-per-route:200}")
    private int poolMaxPerRoute;

    @Bean
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactory requestFactory;

        try {
            // Try Apache HttpClient 5 for connection pooling
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(poolMaxTotal);
            connManager.setDefaultMaxPerRoute(poolMaxPerRoute);
            connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                    .setSocketTimeout(Timeout.ofMilliseconds(readTimeout))
                    .build());

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connManager)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout))
                            .build())
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .build();

            requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        } catch (NoClassDefFoundError e) {
            // Fallback to default factory if Apache HttpClient not on classpath
            ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofMillis(connectTimeout))
                    .withReadTimeout(Duration.ofMillis(readTimeout));
            requestFactory = ClientHttpRequestFactories.get(settings);
        }

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
