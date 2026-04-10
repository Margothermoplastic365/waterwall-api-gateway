package com.gateway.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.gateway.management", "com.gateway.common"})
@EnableCaching
@EnableScheduling
public class ManagementApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagementApiApplication.class, args);
    }

    /**
     * Configurable delay for wallet deduction scheduler.
     * Reads from platform_settings table; defaults to 5 minutes (300000ms).
     */
    @org.springframework.context.annotation.Bean
    public Long walletDeductionDelay(com.gateway.management.service.PlatformSettingsService platformSettingsService) {
        return (long) platformSettingsService.getWalletDeductionIntervalMinutes() * 60 * 1000;
    }
}
