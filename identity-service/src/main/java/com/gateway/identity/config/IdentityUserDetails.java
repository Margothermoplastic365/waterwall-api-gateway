package com.gateway.identity.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/**
 * Extended UserDetails that carries the identity-service specific user ID and organization ID
 * through the Spring Security authentication context.
 */
public class IdentityUserDetails extends User {

    private final UUID userId;
    private final UUID orgId;

    public IdentityUserDetails(String username, String password, boolean enabled,
                               boolean accountNonExpired, boolean credentialsNonExpired,
                               boolean accountNonLocked,
                               Collection<? extends GrantedAuthority> authorities,
                               UUID userId, UUID orgId) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId = userId;
        this.orgId = orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrgId() {
        return orgId;
    }
}
