package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.dto.ApiErrorResponse;
import com.gateway.runtime.dto.FaultConfig;
import com.gateway.runtime.model.MatchedRoute;
import com.gateway.runtime.service.ChaosService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Order(48) — Chaos/fault injection filter. Applies configured faults
 * (latency, error rate, timeout, connection refused) for testing purposes.
 */
@Slf4j
@Component
@Order(48)
@RequiredArgsConstructor
public class ChaosFilter implements Filter {

    private final ChaosService chaosService;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        MatchedRoute matchedRoute = (MatchedRoute) request.getAttribute(RouteMatchFilter.ATTR_MATCHED_ROUTE);
        if (matchedRoute == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        UUID apiId = matchedRoute.getRoute().getApiId();
        FaultConfig config = chaosService.getFaultConfig(apiId);
        if (config == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Simulate connection refused
        if (config.isSimulateConnectionRefused()) {
            log.debug("Chaos: simulating connection refused for API {}", apiId);
            writeError(response, request, 502, "BAD_GATEWAY", "GW_502",
                    "Connection refused (chaos injection)");
            return;
        }

        // Simulate timeout
        if (config.isSimulateTimeout()) {
            log.debug("Chaos: simulating timeout for API {}", apiId);
            try {
                Thread.sleep(60_000); // 60s timeout simulation
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeError(response, request, 504, "GATEWAY_TIMEOUT", "GW_504",
                    "Gateway timeout (chaos injection)");
            return;
        }

        // Add latency
        if (config.getAddLatencyMs() > 0) {
            log.debug("Chaos: adding {}ms latency for API {}", config.getAddLatencyMs(), apiId);
            try {
                Thread.sleep(config.getAddLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Error rate
        if (config.getErrorRatePercent() > 0) {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < config.getErrorRatePercent()) {
                log.debug("Chaos: injecting 500 error for API {} (roll={}, rate={}%)",
                        apiId, roll, config.getErrorRatePercent());
                writeError(response, request, 500, "INTERNAL_SERVER_ERROR", "GW_500",
                        "Internal server error (chaos injection)");
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            int status, String error, String errorCode, String message) throws IOException {
        ApiErrorResponse errorBody = ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .build();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
