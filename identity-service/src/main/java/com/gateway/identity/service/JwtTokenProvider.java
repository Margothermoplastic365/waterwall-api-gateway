package com.gateway.identity.service;

import com.gateway.identity.entity.UserEntity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Simple JWT token provider using Nimbus JOSE with HMAC-SHA256 symmetric signing.
 * <p>
 * This is an interim implementation. The full Spring Authorization Server setup
 * will replace this with asymmetric (RSA/EC) key-based signing configured via
 * {@code spring-security-oauth2-authorization-server}.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration MFA_SESSION_TTL = Duration.ofMinutes(5);

    @Value("${gateway.identity.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${gateway.identity.jwt.key-store-password}")
    private String signingSecret;

    private JWSSigner signer;

    @PostConstruct
    void init() {
        try {
            // Derive a 256-bit key from the configured secret using SHA-256
            byte[] keyBytes = deriveKey(signingSecret);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            this.signer = new MACSigner(secretKey);
            log.info("JWT token provider initialised with HMAC-SHA256 signing");
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to initialise JWT signer", ex);
        }
    }

    /**
     * Generate a short-lived access token for the given user.
     *
     * @param user the authenticated user entity
     * @return signed JWT access token string
     */
    public String generateAccessToken(UserEntity user) {
        return generateAccessToken(user, List.of());
    }

    /**
     * Generate a short-lived access token for the given user with roles.
     *
     * @param user  the authenticated user entity
     * @param roles list of role names to include in the token
     * @return signed JWT access token string
     */
    public String generateAccessToken(UserEntity user, List<String> roles) {
        return generateAccessToken(user, roles, List.of());
    }

    /**
     * Generate a short-lived access token with roles and permissions.
     */
    public String generateAccessToken(UserEntity user, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ACCESS_TOKEN_TTL);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("email_verified", user.getEmailVerified())
                .claim("token_type", "access")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry));

        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", roles);
        }

        if (permissions != null && !permissions.isEmpty()) {
            builder.claim("permissions", permissions);
        }

        return sign(builder.build());
    }

    /**
     * Generate a long-lived refresh token for the given user.
     *
     * @param user the authenticated user entity
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(REFRESH_TOKEN_TTL);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .subject(user.getId().toString())
                .claim("token_type", "refresh")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();

        return sign(claims);
    }

    /**
     * Generate a short-lived MFA session token. This token is issued after successful
     * password verification when MFA is required, and must be presented when completing
     * the MFA challenge.
     *
     * @param user the user who needs to complete MFA
     * @return signed JWT MFA session token string (5-minute TTL)
     */
    public String generateMfaSessionToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(MFA_SESSION_TTL);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .subject(user.getId().toString())
                .claim("token_type", "mfa_session")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();

        return sign(claims);
    }

    /**
     * Returns the access token time-to-live in seconds.
     */
    public long getAccessTokenTtlSeconds() {
        return ACCESS_TOKEN_TTL.getSeconds();
    }

    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    /**
     * Derive a 256-bit key from the input secret via SHA-256.
     * This ensures a consistent key length regardless of input length.
     */
    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
