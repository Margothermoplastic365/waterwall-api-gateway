package com.gateway.common.auth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AOP aspect that enforces {@link RequiresPermission} annotations.
 * <p>
 * Users with the {@code SUPER_ADMIN} role bypass all permission checks.
 */
@Aspect
@Component
public class PermissionAspect {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequiresPermission requiresPermission) throws Throwable {

        if (!SecurityContextHelper.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        // SUPER_ADMIN bypasses all permission checks
        List<String> roles = SecurityContextHelper.getCurrentRoles();
        if (roles.contains(SUPER_ADMIN_ROLE)) {
            return joinPoint.proceed();
        }

        String requiredPermission = requiresPermission.value();
        String[] anyPermissions = requiresPermission.any();

        boolean granted = false;

        // Check the single required permission
        if (!requiredPermission.isEmpty() && SecurityContextHelper.hasPermission(requiredPermission)) {
            granted = true;
        }

        // Check OR-logic permissions
        if (!granted && anyPermissions.length > 0
                && SecurityContextHelper.hasAnyPermission(anyPermissions)) {
            granted = true;
        }

        // If neither value() nor any() was specified the annotation is effectively a no-op
        if (!granted && ((!requiredPermission.isEmpty()) || anyPermissions.length > 0)) {
            throw new AccessDeniedException(
                    "Access denied — missing required permission: "
                            + (!requiredPermission.isEmpty() ? requiredPermission : String.join(" | ", anyPermissions)));
        }

        return joinPoint.proceed();
    }
}
