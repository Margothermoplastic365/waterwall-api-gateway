package com.gateway.identity.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.common.cache.CacheInvalidationPublisher;
import com.gateway.common.cache.CacheNames;
import com.gateway.identity.entity.PermissionEntity;
import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.RoleEntity;
import com.gateway.identity.entity.enums.ScopeType;
import com.gateway.identity.repository.RoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RBAC permission evaluator.
 * <p>
 * Named {@code "perm"} so it can be referenced in SpEL expressions:
 * <pre>
 * &#64;PreAuthorize("@perm.check('api:create')")
 * &#64;PreAuthorize("@perm.check('api:update', #apiId)")
 * &#64;PreAuthorize("@perm.checkAny('api:read', 'api:admin')")
 * </pre>
 */
@Slf4j
@Component("perm")
@RequiredArgsConstructor
public class PermissionEvaluator {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String WILDCARD = "*";

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final CacheManager cacheManager;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    /**
     * Checks whether the currently authenticated user holds the given permission.
     * GLOBAL and ORG-scoped role assignments are considered.
     *
     * @param permission permission string formatted as "resource:action"
     * @return {@code true} if the user has the permission
     */
    public boolean check(String permission) {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            return false;
        }

        Set<String> permissions = getEffectivePermissions(UUID.fromString(userId));
        return permissions.contains(WILDCARD) || permissions.contains(permission);
    }

    /**
     * Checks whether the currently authenticated user holds the given permission,
     * also considering RESOURCE-scoped role assignments where the scopeId matches
     * the provided resourceId.
     *
     * @param permission permission string formatted as "resource:action"
     * @param resourceId the specific resource ID to check RESOURCE-scoped assignments against
     * @return {@code true} if the user has the permission
     */
    public boolean check(String permission, UUID resourceId) {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            return false;
        }

        UUID userUuid = UUID.fromString(userId);

        // Check GLOBAL + ORG permissions first (cached)
        Set<String> permissions = getEffectivePermissions(userUuid);
        if (permissions.contains(WILDCARD) || permissions.contains(permission)) {
            return true;
        }

        // Check RESOURCE-scoped assignments for the specific resource
        Set<String> resourcePermissions = getResourceScopedPermissions(userUuid, resourceId);
        return resourcePermissions.contains(permission);
    }

    /**
     * Checks whether the currently authenticated user holds ANY of the listed permissions.
     *
     * @param permissions permission strings formatted as "resource:action"
     * @return {@code true} if the user has at least one of the permissions
     */
    public boolean checkAny(String... permissions) {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null) {
            return false;
        }

        Set<String> effective = getEffectivePermissions(UUID.fromString(userId));
        if (effective.contains(WILDCARD)) {
            return true;
        }

        for (String permission : permissions) {
            if (effective.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the effective set of permissions for the given user, considering
     * GLOBAL assignments and ORG-scoped assignments matching the current org context.
     * Results are cached in the "permissions" Caffeine cache.
     *
     * @param userId the user ID
     * @return set of permission strings formatted as "resource:action", or {"*"} for SUPER_ADMIN
     */
    public Set<String> getEffectivePermissions(UUID userId) {
        Cache cache = cacheManager.getCache(CacheNames.PERMISSIONS);
        if (cache != null) {
            Set<String> cached = cache.get(userId.toString(), Set.class);
            if (cached != null) {
                return cached;
            }
        }

        // Cache miss — compute from database
        List<RoleAssignmentEntity> activeAssignments =
                roleAssignmentRepository.findActiveByUserId(userId);

        // Filter to GLOBAL and ORG-scoped (matching current org) assignments
        String currentOrgId = SecurityContextHelper.getCurrentOrgId();
        List<RoleAssignmentEntity> applicableAssignments = activeAssignments.stream()
                .filter(assignment -> isApplicableAssignment(assignment, currentOrgId))
                .toList();

        // Check for SUPER_ADMIN
        boolean isSuperAdmin = applicableAssignments.stream()
                .map(RoleAssignmentEntity::getRole)
                .map(RoleEntity::getName)
                .anyMatch(SUPER_ADMIN_ROLE::equals);

        if (isSuperAdmin) {
            Set<String> wildcardSet = Set.of(WILDCARD);
            cacheResult(cache, userId, wildcardSet);
            log.debug("User {} is SUPER_ADMIN — granting wildcard permissions", userId);
            return wildcardSet;
        }

        // Union all permissions from all applicable roles
        Set<String> permissions = applicableAssignments.stream()
                .map(RoleAssignmentEntity::getRole)
                .map(RoleEntity::getPermissions)
                .flatMap(Collection::stream)
                .map(PermissionEvaluator::formatPermission)
                .collect(Collectors.toUnmodifiableSet());

        cacheResult(cache, userId, permissions);
        log.debug("Computed {} effective permissions for user {}", permissions.size(), userId);
        return permissions;
    }

    /**
     * Evicts the cached permissions for the given user and publishes a cache
     * invalidation event so all cluster nodes also evict their local copies.
     *
     * @param userId the user whose permission cache should be invalidated
     */
    public void invalidateCache(UUID userId) {
        Cache cache = cacheManager.getCache(CacheNames.PERMISSIONS);
        if (cache != null) {
            cache.evict(userId.toString());
        }

        cacheInvalidationPublisher.invalidate(
                CacheNames.PERMISSIONS,
                userId.toString(),
                "Permission change for user " + userId
        );
        log.info("Invalidated permission cache for user {}", userId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether a role assignment is applicable in the current context
     * (GLOBAL always applies; ORG applies if the scopeId matches the current org).
     * RESOURCE-scoped assignments are excluded here — they are evaluated per-resource.
     */
    private boolean isApplicableAssignment(RoleAssignmentEntity assignment, String currentOrgId) {
        return switch (assignment.getScopeType()) {
            case GLOBAL -> true;
            case ORG -> currentOrgId != null
                    && assignment.getScopeId() != null
                    && assignment.getScopeId().toString().equals(currentOrgId);
            case RESOURCE -> false; // evaluated separately in check(permission, resourceId)
        };
    }

    /**
     * Loads RESOURCE-scoped permissions for a specific user and resource.
     * These are not cached because they are resource-specific.
     */
    private Set<String> getResourceScopedPermissions(UUID userId, UUID resourceId) {
        List<RoleAssignmentEntity> resourceAssignments =
                roleAssignmentRepository.findByUserIdAndScopeTypeAndScopeId(
                        userId, ScopeType.RESOURCE, resourceId);

        return resourceAssignments.stream()
                .map(RoleAssignmentEntity::getRole)
                .map(RoleEntity::getPermissions)
                .flatMap(Collection::stream)
                .map(PermissionEvaluator::formatPermission)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String formatPermission(PermissionEntity permission) {
        return permission.getResource() + ":" + permission.getAction();
    }

    private void cacheResult(Cache cache, UUID userId, Set<String> permissions) {
        if (cache != null) {
            cache.put(userId.toString(), permissions);
        }
    }
}
