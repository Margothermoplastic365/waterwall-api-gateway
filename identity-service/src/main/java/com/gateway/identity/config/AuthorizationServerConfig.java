package com.gateway.identity.config;

import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
public class AuthorizationServerConfig {

    @Value("${gateway.identity.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${gateway.identity.oauth2.default-client-id:gateway-portal}")
    private String defaultClientId;

    @Value("${gateway.identity.oauth2.default-client-secret:change-me}")
    private String defaultClientSecret;

    @Value("${gateway.identity.oauth2.default-redirect-uri:http://localhost:3000/api/auth/callback}")
    private String defaultRedirectUri;

    @Value("${gateway.identity.oauth2.access-token-ttl-hours:1}")
    private long accessTokenTtlHours;

    @Value("${gateway.identity.oauth2.refresh-token-ttl-days:30}")
    private long refreshTokenTtlDays;

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate,
                                                                  PasswordEncoder passwordEncoder) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);

        // Register the default gateway-portal client if it doesn't already exist
        if (repository.findByClientId(defaultClientId) == null) {
            RegisteredClient gatewayPortalClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(defaultClientId)
                    .clientSecret(passwordEncoder.encode(defaultClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri(defaultRedirectUri)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofHours(accessTokenTtlHours))
                            .refreshTokenTimeToLive(Duration.ofDays(refreshTokenTtlDays))
                            .reuseRefreshTokens(false)
                            .build())
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(false)
                            .requireProofKey(false)
                            .build())
                    .build();

            repository.save(gatewayPortalClient);
        }

        return repository;
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(
            RoleAssignmentRepository roleAssignmentRepository) {
        return context -> {
            if (context.getPrincipal() != null && context.getPrincipal().getPrincipal() instanceof IdentityUserDetails userDetails) {
                UUID userId = userDetails.getUserId();
                UUID orgId = userDetails.getOrgId();

                context.getClaims().claim("sub", userId.toString());
                if (orgId != null) {
                    context.getClaims().claim("org_id", orgId.toString());
                }

                List<RoleAssignmentEntity> assignments = roleAssignmentRepository.findActiveByUserId(userId);

                List<String> roles = assignments.stream()
                        .map(ra -> ra.getRole().getName())
                        .distinct()
                        .collect(Collectors.toList());

                List<String> permissions = assignments.stream()
                        .flatMap(ra -> ra.getRole().getPermissions().stream())
                        .map(p -> p.getResource() + ":" + p.getAction())
                        .distinct()
                        .collect(Collectors.toList());

                context.getClaims().claim("roles", roles);
                context.getClaims().claim("permissions", permissions);
            }
        };
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSetUri(issuerUri + "/oauth2/jwks").build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
