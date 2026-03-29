package com.gateway.identity.config;

import com.gateway.identity.entity.OrgMemberEntity;
import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.OrgMemberRepository;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.gateway.identity.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import com.gateway.common.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${gateway.identity.jwt.key-store-password}")
    private String jwtSigningSecret;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      CorsConfigurationSource corsConfigurationSource) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());

        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                ));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          CorsConfigurationSource corsConfigurationSource) throws Exception {
        // Build an HMAC-based JwtDecoder matching the JwtTokenProvider's signing key
        byte[] keyBytes = deriveHmacKey(jwtSigningSecret);
        SecretKey hmacKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        JwtDecoder hmacJwtDecoder = NimbusJwtDecoder.withSecretKey(hmacKey).build();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/v1/auth/register",
                                "/v1/auth/login",
                                "/v1/auth/verify-email",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password",
                                "/v1/internal/**",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(hmacJwtDecoder), UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/v1/")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                            } else {
                                new LoginUrlAuthenticationEntryPoint("/login")
                                        .commence(request, response, authException);
                            }
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);
        delegating.setDefaultPasswordEncoderForMatches(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        return delegating;
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository,
                                                  RoleAssignmentRepository roleAssignmentRepository,
                                                  OrgMemberRepository orgMemberRepository) {
        return email -> {
            UserEntity user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            boolean enabled = user.getStatus() == UserStatus.ACTIVE;
            boolean accountNonLocked = user.getStatus() != UserStatus.LOCKED
                    && (user.getLockedUntil() == null || user.getLockedUntil().isBefore(java.time.Instant.now()));
            boolean accountNonExpired = user.getStatus() != UserStatus.DEACTIVATED;

            List<RoleAssignmentEntity> assignments = roleAssignmentRepository.findActiveByUserId(user.getId());

            Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
            for (RoleAssignmentEntity assignment : assignments) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + assignment.getRole().getName().toUpperCase()));
                assignment.getRole().getPermissions().forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(
                                permission.getResource() + ":" + permission.getAction())));
            }

            // Resolve primary organization (first membership found)
            UUID orgId = orgMemberRepository.findByUserId(user.getId()).stream()
                    .findFirst()
                    .map(OrgMemberEntity::getOrganization)
                    .map(org -> org.getId())
                    .orElse(null);

            return new IdentityUserDetails(
                    user.getEmail(),
                    user.getPasswordHash(),
                    enabled,
                    accountNonExpired,
                    true, // credentialsNonExpired
                    accountNonLocked,
                    authorities,
                    user.getId(),
                    orgId
            );
        };
    }

    private static byte[] deriveHmacKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
