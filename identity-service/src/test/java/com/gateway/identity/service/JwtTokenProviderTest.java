package com.gateway.identity.service;

import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();

        // Set required @Value fields via reflection
        ReflectionTestUtils.setField(jwtTokenProvider, "issuerUri", "https://gateway.example.com");
        ReflectionTestUtils.setField(jwtTokenProvider, "signingSecret", "my-super-secret-signing-key-for-tests-1234567890");

        // Trigger @PostConstruct init
        jwtTokenProvider.init();

        user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("jwt@example.com")
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ── generateAccessToken tests ───────────────────────────────────────

    @Test
    void generateAccessToken_noRoles_returnsValidJwt() throws ParseException {
        String token = jwtTokenProvider.generateAccessToken(user);

        assertThat(token).isNotNull().isNotEmpty();

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("https://gateway.example.com");
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("email")).isEqualTo("jwt@example.com");
        assertThat(claims.getBooleanClaim("email_verified")).isTrue();
        assertThat(claims.getStringClaim("token_type")).isEqualTo("access");
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getIssueTime()).isBeforeOrEqualTo(new Date());
        assertThat(claims.getExpirationTime()).isAfter(new Date());
    }

    @Test
    void generateAccessToken_withRoles_includesRolesClaim() throws ParseException {
        List<String> roles = List.of("ADMIN", "API_VIEWER");

        String token = jwtTokenProvider.generateAccessToken(user, roles);

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        List<String> tokenRoles = claims.getStringListClaim("roles");
        assertThat(tokenRoles).containsExactlyInAnyOrder("ADMIN", "API_VIEWER");
    }

    @Test
    void generateAccessToken_withRolesAndPermissions_includesBothClaims() throws ParseException {
        List<String> roles = List.of("ADMIN");
        List<String> permissions = List.of("api:read", "api:write");

        String token = jwtTokenProvider.generateAccessToken(user, roles, permissions);

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat(claims.getStringListClaim("roles")).containsExactly("ADMIN");
        assertThat(claims.getStringListClaim("permissions")).containsExactlyInAnyOrder("api:read", "api:write");
    }

    @Test
    void generateAccessToken_emptyRoles_noRolesClaim() throws ParseException {
        String token = jwtTokenProvider.generateAccessToken(user, List.of(), List.of());

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat(claims.getClaim("roles")).isNull();
        assertThat(claims.getClaim("permissions")).isNull();
    }

    @Test
    void generateAccessToken_expiresIn30Minutes() throws ParseException {
        String token = jwtTokenProvider.generateAccessToken(user);

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        Instant issuedAt = claims.getIssueTime().toInstant();
        Instant expiresAt = claims.getExpirationTime().toInstant();
        long secondsDiff = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();

        // 30 minutes = 1800 seconds (allow small tolerance)
        assertThat(secondsDiff).isBetween(1798L, 1802L);
    }

    // ── generateRefreshToken tests ──────────────────────────────────────

    @Test
    void generateRefreshToken_returnsValidJwt() throws ParseException {
        String token = jwtTokenProvider.generateRefreshToken(user);

        assertThat(token).isNotNull();

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("token_type")).isEqualTo("refresh");
        assertThat(claims.getIssuer()).isEqualTo("https://gateway.example.com");
    }

    @Test
    void generateRefreshToken_expiresIn7Days() throws ParseException {
        String token = jwtTokenProvider.generateRefreshToken(user);

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        Instant issuedAt = claims.getIssueTime().toInstant();
        Instant expiresAt = claims.getExpirationTime().toInstant();
        long daysDiff = java.time.Duration.between(issuedAt, expiresAt).toDays();

        assertThat(daysDiff).isEqualTo(7);
    }

    // ── generateMfaSessionToken tests ───────────────────────────────────

    @Test
    void generateMfaSessionToken_returnsValidJwt() throws ParseException {
        String token = jwtTokenProvider.generateMfaSessionToken(user);

        assertThat(token).isNotNull();

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("token_type")).isEqualTo("mfa_session");
    }

    @Test
    void generateMfaSessionToken_expiresIn5Minutes() throws ParseException {
        String token = jwtTokenProvider.generateMfaSessionToken(user);

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        Instant issuedAt = claims.getIssueTime().toInstant();
        Instant expiresAt = claims.getExpirationTime().toInstant();
        long secondsDiff = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();

        // 5 minutes = 300 seconds
        assertThat(secondsDiff).isBetween(298L, 302L);
    }

    // ── getAccessTokenTtlSeconds tests ──────────────────────────────────

    @Test
    void getAccessTokenTtlSeconds_returns1800() {
        long ttl = jwtTokenProvider.getAccessTokenTtlSeconds();

        assertThat(ttl).isEqualTo(1800L);
    }

    // ── Token uniqueness ────────────────────────────────────────────────

    @Test
    void generatedTokens_haveUniqueJwtIds() throws ParseException {
        String token1 = jwtTokenProvider.generateAccessToken(user);
        String token2 = jwtTokenProvider.generateAccessToken(user);

        String jti1 = SignedJWT.parse(token1).getJWTClaimsSet().getJWTID();
        String jti2 = SignedJWT.parse(token2).getJWTClaimsSet().getJWTID();

        assertThat(jti1).isNotEqualTo(jti2);
    }
}
