package com.gateway.runtime.filter;

import com.gateway.common.auth.GatewayAuthentication;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;

/**
 * Order(19) — mTLS authentication filter. Runs just before {@link GatewayAuthFilter}.
 * <p>
 * Extracts the client certificate from the TLS handshake (via the servlet container
 * attribute {@code jakarta.servlet.request.X509Certificate}), computes the SHA-256
 * fingerprint, and validates it against the identity-service.
 * <p>
 * If validation succeeds, a {@link GatewayAuthentication} is placed in the
 * {@link SecurityContextHolder} so that {@link GatewayAuthFilter} will skip
 * its own authentication logic.
 * <p>
 * If no client certificate is present, this filter is a no-op and the chain continues.
 */
@Slf4j
@Component
@Order(19)
public class MtlsAuthFilter implements Filter {

    public static final String ATTR_CLIENT_CERT_CN = "gateway.clientCertCN";
    public static final String ATTR_CLIENT_CERT_FINGERPRINT = "gateway.clientCertFingerprint";
    public static final String AUTH_TYPE_MTLS = "MTLS";

    private final RestClient restClient;
    private final Cache<String, MtlsCertInfo> mtlsCertCache;

    public MtlsAuthFilter(
            @Value("${gateway.runtime.identity-service-url}") String identityServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
        this.mtlsCertCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(5_000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certs != null && certs.length > 0) {
            X509Certificate clientCert = certs[0];
            String cn = extractCn(clientCert.getSubjectX500Principal().getName());
            String fingerprint = computeFingerprint(clientCert);

            log.debug("Client certificate detected: CN={}, fingerprint={}", cn, fingerprint);

            String cacheKey = cn + ":" + fingerprint;
            MtlsCertInfo certInfo = mtlsCertCache.get(cacheKey, key -> {
                log.debug("mTLS cache miss for CN={}, calling identity-service", cn);
                return validateCertWithIdentityService(cn, fingerprint);
            });

            if (certInfo != null) {
                GatewayAuthentication authentication = new GatewayAuthentication(
                        certInfo.appId(),
                        certInfo.orgId(),
                        null,       // no email for mTLS auth
                        Collections.emptyList(),
                        Collections.emptyList(),
                        certInfo.appId(),
                        null
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(GatewayAuthFilter.ATTR_AUTH_TYPE, AUTH_TYPE_MTLS);
                request.setAttribute(ATTR_CLIENT_CERT_CN, cn);
                request.setAttribute(ATTR_CLIENT_CERT_FINGERPRINT, fingerprint);
                request.setAttribute("gateway.consumerId", certInfo.appId());
                request.setAttribute("gateway.applicationId", certInfo.appId());

                // Forward headers to upstream — set as attributes for the proxy to pick up
                request.setAttribute("X-Client-Cert-CN", cn);
                request.setAttribute("X-Client-Cert-Fingerprint", fingerprint);

                log.debug("mTLS authenticated: appId={}, CN={}", certInfo.appId(), cn);
            } else {
                log.warn("mTLS validation failed for CN={}, fingerprint={}", cn, fingerprint);
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private MtlsCertInfo validateCertWithIdentityService(String cn, String fingerprint) {
        try {
            return restClient.get()
                    .uri("/v1/internal/validate-cert?cn={cn}&fingerprint={fp}", cn, fingerprint)
                    .retrieve()
                    .body(MtlsCertInfo.class);
        } catch (Exception e) {
            log.error("Failed to validate client certificate with identity-service: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the CN (Common Name) from an X.500 distinguished name string.
     */
    private String extractCn(String dn) {
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return dn;
    }

    /**
     * Compute the SHA-256 fingerprint of a certificate as a lowercase hex string.
     */
    private String computeFingerprint(X509Certificate cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException("Failed to compute certificate fingerprint", e);
        }
    }

    /**
     * Record returned by the identity-service cert validation endpoint.
     */
    record MtlsCertInfo(
            String appId,
            String applicationName,
            String userId,
            String orgId
    ) {}
}
