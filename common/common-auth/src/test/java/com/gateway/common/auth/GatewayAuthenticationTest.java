package com.gateway.common.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationTest {

    @Test
    void shouldSetAuthenticatedToTrue() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", "user@example.com",
                List.of("ADMIN"), List.of("read"), "app1", "openid");

        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldReturnUserIdAsPrincipal() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user42", "org1", "u@example.com",
                List.of(), List.of(), "app1", "openid");

        assertThat(auth.getPrincipal()).isEqualTo("user42");
    }

    @Test
    void shouldReturnNullCredentials() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", "u@example.com",
                List.of(), List.of(), "app1", null);

        assertThat(auth.getCredentials()).isNull();
    }

    @Test
    void shouldMapRolesToAuthoritiesWithPrefix() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", null,
                List.of("ADMIN", "USER"), List.of(), null, null);

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorityNames).containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void shouldNotDuplicateRolePrefix() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", null,
                List.of("ROLE_ADMIN"), List.of(), null, null);

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorityNames).containsExactly("ROLE_ADMIN");
    }

    @Test
    void shouldHandleNullRoles() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", null,
                null, List.of(), null, null);

        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.getRoles()).isEmpty();
    }

    @Test
    void shouldHandleNullPermissions() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", null,
                List.of(), null, null, null);

        assertThat(auth.getPermissions()).isEmpty();
    }

    @Test
    void shouldCreateDefensiveCopyOfRoles() {
        List<String> roles = new ArrayList<>(List.of("ADMIN", "USER"));
        List<String> permissions = new ArrayList<>(List.of("read", "write"));

        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", null,
                roles, permissions, null, null);

        roles.add("SUPERADMIN");
        permissions.add("delete");

        assertThat(auth.getRoles()).containsExactly("ADMIN", "USER");
        assertThat(auth.getPermissions()).containsExactly("read", "write");
    }

    @Test
    void shouldExposeAllFields() {
        GatewayAuthentication auth = new GatewayAuthentication(
                "user1", "org1", "user@example.com",
                List.of("ADMIN"), List.of("read"), "app1", "openid profile");

        assertThat(auth.getUserId()).isEqualTo("user1");
        assertThat(auth.getOrgId()).isEqualTo("org1");
        assertThat(auth.getEmail()).isEqualTo("user@example.com");
        assertThat(auth.getAppId()).isEqualTo("app1");
        assertThat(auth.getScope()).isEqualTo("openid profile");
    }
}
