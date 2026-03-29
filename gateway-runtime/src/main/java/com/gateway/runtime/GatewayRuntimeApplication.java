package com.gateway.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.gateway.runtime", "com.gateway.common"})
@EnableCaching
@EnableScheduling
public class GatewayRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayRuntimeApplication.class, args);
    }
}
