package com.gateway.common.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * Static utility for reading the current {@link GatewayAuthentication} from the
 * {@link SecurityContextHolder}.  All methods are null-safe and return sensible
 * defaults when no authentication is present.
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {
        // utility class
    }

    /**
     * Returns the current {@link GatewayAuthentication}, or {@code null} if the
     * security context does not hold one.
     */
    private static GatewayAuthentication current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof GatewayAuthentication gatewayAuth) {
            return gatewayAuth;
        }
        return null;
    }

    public static String getCurrentUserId() {
        GatewayAuthentication auth = current();
        return auth != null ? auth.getUserId() : null;
    }

    public static String getCurrentOrgId() {
        GatewayAuthentication auth = current();
        return auth != null ? auth.getOrgId() : null;
    }

    public static String getCurrentEmail() {
        GatewayAuthentication auth = current();
        return auth != null ? auth.getEmail() : null;
    }

    public static List<String> getCurrentPermissions() {
        GatewayAuthentication auth = current();
        return auth != null ? auth.getPermissions() : Collections.emptyList();
    }

    public static List<String> getCurrentRoles() {
        GatewayAuthentication auth = current();
        return auth != null ? auth.getRoles() : Collections.emptyList();
    }

    public static boolean hasPermission(String permission) {
        return getCurrentPermissions().contains(permission);
    }

    public static boolean hasAnyPermission(String... permissions) {
        List<String> current = getCurrentPermissions();
        for (String p : permissions) {
            if (current.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuthenticated() {
        GatewayAuthentication auth = current();
        return auth != null && auth.isAuthenticated();
    }
}
