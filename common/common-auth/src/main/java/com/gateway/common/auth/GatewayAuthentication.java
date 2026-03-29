package com.gateway.common.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom authentication token carrying all gateway-relevant identity information
 * extracted from a JWT or an API-key lookup.
 */
public class GatewayAuthentication extends AbstractAuthenticationToken {

    private final String userId;
    private final String orgId;
    private final String email;
    private final List<String> roles;
    private final List<String> permissions;
    private final String appId;
    private final String scope;

    public GatewayAuthentication(String userId,
                                 String orgId,
                                 String email,
                                 List<String> roles,
                                 List<String> permissions,
                                 String appId,
                                 String scope) {
        super(mapRolesToAuthorities(roles));
        this.userId = userId;
        this.orgId = orgId;
        this.email = email;
        this.roles = roles == null ? Collections.emptyList() : List.copyOf(roles);
        this.permissions = permissions == null ? Collections.emptyList() : List.copyOf(permissions);
        this.appId = appId;
        this.scope = scope;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> mapRolesToAuthorities(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Object getCredentials() {
        return null; // credentials are not retained after authentication
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getAppId() {
        return appId;
    }

    public String getScope() {
        return scope;
    }
}
