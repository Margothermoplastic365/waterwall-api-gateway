package com.gateway.identity.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Social login configuration for Google, GitHub, and Azure AD.
 *
 * <p>Enable social login by setting the following properties in application.yml:
 * <pre>
 * gateway.identity.social.enabled: true
 * spring.security.oauth2.client.registration.google.client-id: your-client-id
 * spring.security.oauth2.client.registration.google.client-secret: your-client-secret
 * spring.security.oauth2.client.registration.github.client-id: your-client-id
 * spring.security.oauth2.client.registration.github.client-secret: your-client-secret
 * spring.security.oauth2.client.registration.azure.client-id: your-client-id
 * spring.security.oauth2.client.registration.azure.client-secret: your-client-secret
 * </pre>
 *
 * <p>When enabled, Spring Security OAuth2 Client auto-configuration picks up the
 * client registration properties and provides the OAuth2 login flow automatically.
 * The {@link SocialLoginService} handles linking social accounts to internal users.
 */
@Configuration
@ConditionalOnProperty(name = "gateway.identity.social.enabled", havingValue = "true", matchIfMissing = false)
public class SocialLoginConfig {

    // Spring Boot auto-configures OAuth2 client from application.yml properties.
    // This configuration class serves as the enablement gate and documentation anchor.
    // Additional customization (custom OAuth2UserService, success handlers) can be added here.
}
