package com.gateway.runtime.service;

import com.gateway.runtime.dto.FaultConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChaosService {

    private final Map<UUID, FaultConfig> faultConfigs = new ConcurrentHashMap<>();

    public void enableFaultInjection(UUID apiId, FaultConfig config) {
        faultConfigs.put(apiId, config);
        log.info("Enabled fault injection for API {}: latency={}ms, errorRate={}%, timeout={}, connRefused={}",
                apiId, config.getAddLatencyMs(), config.getErrorRatePercent(),
                config.isSimulateTimeout(), config.isSimulateConnectionRefused());
    }

    public void disableFaultInjection(UUID apiId) {
        faultConfigs.remove(apiId);
        log.info("Disabled fault injection for API {}", apiId);
    }

    public FaultConfig getFaultConfig(UUID apiId) {
        return faultConfigs.get(apiId);
    }

    public boolean hasFaultInjection(UUID apiId) {
        return faultConfigs.containsKey(apiId);
    }
}
