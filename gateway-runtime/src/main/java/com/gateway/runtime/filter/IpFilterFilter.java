package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.dto.ApiErrorResponse;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Order(6) — Checks the client IP against configurable whitelist and blacklist,
 * with support for CIDR notation. Blocked requests receive a 403 response.
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class IpFilterFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Value("${gateway.runtime.security.ip-whitelist:}")
    private String ipWhitelistConfig;

    @Value("${gateway.runtime.security.ip-blacklist:}")
    private String ipBlacklistConfig;

    @Value("${gateway.runtime.security.trust-x-forwarded-for:true}")
    private boolean trustXForwardedFor;

    private List<CidrRange> whitelist;
    private List<CidrRange> blacklist;

    @PostConstruct
    public void init() {
        whitelist = parseCidrList(ipWhitelistConfig);
        blacklist = parseCidrList(ipBlacklistConfig);
        if (!whitelist.isEmpty()) {
            log.info("IP whitelist configured with {} entries", whitelist.size());
        }
        if (!blacklist.isEmpty()) {
            log.info("IP blacklist configured with {} entries", blacklist.size());
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest request)
                || !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Skip actuator endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Nothing configured — allow all
        if (whitelist.isEmpty() && blacklist.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        // Check blacklist first
        if (!blacklist.isEmpty() && isIpInList(clientIp, blacklist)) {
            log.warn("Blocked blacklisted IP: {}", clientIp);
            writeErrorResponse(response, request, HttpServletResponse.SC_FORBIDDEN,
                    "IP_BLOCKED", "GW_403", "Access denied for IP: " + clientIp);
            return;
        }

        // Check whitelist (if configured, IP must be in it)
        if (!whitelist.isEmpty() && !isIpInList(clientIp, whitelist)) {
            log.warn("Blocked non-whitelisted IP: {}", clientIp);
            writeErrorResponse(response, request, HttpServletResponse.SC_FORBIDDEN,
                    "IP_NOT_ALLOWED", "GW_403", "Access denied for IP: " + clientIp);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustXForwardedFor) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                // Take the first (leftmost) IP — the original client
                String firstIp = xForwardedFor.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }
        return request.getRemoteAddr();
    }

    // Cache InetAddress lookups to avoid per-request DNS resolution
    private static final java.util.concurrent.ConcurrentHashMap<String, InetAddress> inetCache = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean isIpInList(String ip, List<CidrRange> ranges) {
        try {
            InetAddress addr = inetCache.computeIfAbsent(ip, k -> {
                try { return InetAddress.getByName(k); }
                catch (UnknownHostException e) { return null; }
            });
            if (addr == null) return false;
            for (CidrRange range : ranges) {
                if (range.contains(addr)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Cannot parse client IP '{}': {}", ip, e.getMessage());
        }
        return false;
    }

    private List<CidrRange> parseCidrList(String config) {
        List<CidrRange> result = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return result;
        }
        for (String entry : config.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(CidrRange.parse(trimmed));
            } catch (Exception e) {
                log.warn("Invalid CIDR/IP entry '{}': {}", trimmed, e.getMessage());
            }
        }
        return result;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    int status, String error, String errorCode,
                                    String message) throws IOException {
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

    /**
     * Represents a CIDR range (e.g., 10.0.0.0/8) or a single IP address.
     */
    static class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        private CidrRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) throws UnknownHostException {
            if (cidr.contains("/")) {
                String[] parts = cidr.split("/");
                InetAddress addr = InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                return new CidrRange(addr.getAddress(), prefix);
            } else {
                InetAddress addr = InetAddress.getByName(cidr);
                int maxPrefix = addr.getAddress().length * 8; // 32 for IPv4, 128 for IPv6
                return new CidrRange(addr.getAddress(), maxPrefix);
            }
        }

        boolean contains(InetAddress address) {
            byte[] addrBytes = address.getAddress();
            if (addrBytes.length != networkAddress.length) {
                return false; // IPv4 vs IPv6 mismatch
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != networkAddress[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < addrBytes.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((addrBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
