package com.gateway.identity.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.identity.dto.*;
import com.gateway.identity.entity.PermissionEntity;
import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.RoleEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.ScopeType;
import com.gateway.identity.repository.OrganizationRepository;
import com.gateway.identity.repository.PermissionRepository;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.gateway.identity.repository.RoleRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for Role-Based Access Control (RBAC) operations including
 * role management, role assignment, and permission catalog queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;

    // ── Role management ─────────────────────────────────────────────────

    /**
     * Create a new role with the specified permissions.
     *
     * @param request the role creation payload
     * @return the created role
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        roleRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role with name '" + request.getName() + "' already exists");
        });

        Set<PermissionEntity> permissions = resolvePermissions(request.getPermissionIds());

        RoleEntity role = RoleEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .scopeType(request.getScopeType())
                .isSystem(false)
                .permissions(permissions)
                .build();

        role = roleRepository.save(role);

        auditService.logEvent("role.created", "ROLE", role.getId().toString(), "SUCCESS");
        log.info("Created role '{}' with {} permissions", role.getName(), permissions.size());

        return toRoleResponse(role);
    }

    /**
     * List all roles with their permissions.
     *
     * @return list of all roles
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    /**
     * Get a single role by ID, including its permissions.
     *
     * @param roleId the role ID
     * @return the role details
     */
    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID roleId) {
        RoleEntity role = findRoleOrThrow(roleId);
        return toRoleResponse(role);
    }

    /**
     * Update a role's description and permissions.
     * System roles cannot have their name or scopeType modified.
     *
     * @param roleId  the role ID
     * @param request the update payload
     * @return the updated role
     */
    @Transactional
    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        RoleEntity role = findRoleOrThrow(roleId);

        // System roles: only description and permissions can be updated
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissionIds() != null) {
            Set<PermissionEntity> permissions = resolvePermissions(request.getPermissionIds());
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }

        role = roleRepository.save(role);

        // Invalidate permission cache for all users who have this role
        invalidateCacheForRoleUsers(roleId);

        auditService.logEvent("role.updated", "ROLE", role.getId().toString(), "SUCCESS");
        log.info("Updated role '{}'", role.getName());

        return toRoleResponse(role);
    }

    /**
     * Delete a role. System roles and roles with active assignments cannot be deleted.
     *
     * @param roleId the role ID
     */
    @Transactional
    public void deleteRole(UUID roleId) {
        RoleEntity role = findRoleOrThrow(roleId);

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete a system role");
        }

        List<RoleAssignmentEntity> assignments = roleAssignmentRepository.findAll().stream()
                .filter(a -> a.getRole().getId().equals(roleId))
                .toList();

        if (!assignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete role — " + assignments.size() + " active assignment(s) exist");
        }

        roleRepository.delete(role);

        auditService.logEvent("role.deleted", "ROLE", roleId.toString(), "SUCCESS");
        log.info("Deleted role '{}'", role.getName());
    }

    // ── Role assignment ─────────────────────────────────────────────────

    /**
     * Assign a role to a user within a specific scope.
     *
     * @param userId  the target user ID
     * @param request the assignment payload
     * @return the created assignment
     */
    @Transactional
    public RoleAssignmentResponse assignRole(UUID userId, AssignRoleRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        RoleEntity role = findRoleOrThrow(request.getRoleId());

        // Validate scope
        ScopeType scopeType = request.getScopeType();
        if (scopeType == ScopeType.ORG) {
            if (request.getScopeId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "scopeId is required for ORG-scoped assignments");
            }
            if (!organizationRepository.existsById(request.getScopeId())) {
                throw new EntityNotFoundException("Organization not found: " + request.getScopeId());
            }
        }

        String currentUserId = SecurityContextHelper.getCurrentUserId();
        UUID assignedBy = currentUserId != null ? UUID.fromString(currentUserId) : null;

        RoleAssignmentEntity assignment = RoleAssignmentEntity.builder()
                .user(user)
                .role(role)
                .scopeType(scopeType)
                .scopeId(request.getScopeId())
                .expiresAt(request.getExpiresAt())
                .assignedBy(assignedBy)
                .assignedAt(Instant.now())
                .build();

        assignment = roleAssignmentRepository.save(assignment);

        permissionEvaluator.invalidateCache(userId);

        auditService.logEvent("role.assigned", "ROLE_ASSIGNMENT",
                assignment.getId().toString(), "SUCCESS");
        log.info("Assigned role '{}' to user {} (scope={})", role.getName(), userId, scopeType);

        return toRoleAssignmentResponse(assignment);
    }

    /**
     * Revoke a role assignment from a user.
     *
     * @param userId       the user ID that must own the assignment
     * @param assignmentId the assignment ID to revoke
     */
    @Transactional
    public void revokeRole(UUID userId, UUID assignmentId) {
        RoleAssignmentEntity assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Role assignment not found: " + assignmentId));

        if (!assignment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assignment does not belong to user " + userId);
        }

        roleAssignmentRepository.delete(assignment);

        permissionEvaluator.invalidateCache(userId);

        auditService.logEvent("role.revoked", "ROLE_ASSIGNMENT",
                assignmentId.toString(), "SUCCESS");
        log.info("Revoked role assignment {} from user {}", assignmentId, userId);
    }

    /**
     * Get all active (non-expired) role assignments for a user.
     *
     * @param userId the user ID
     * @return list of active assignments
     */
    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> getUserRoleAssignments(UUID userId) {
        return roleAssignmentRepository.findActiveByUserId(userId).stream()
                .map(this::toRoleAssignmentResponse)
                .toList();
    }

    /**
     * Get the effective permissions for a user, combining all role assignments.
     *
     * @param userId the user ID
     * @return effective permissions response including permissions and role assignments
     */
    @Transactional(readOnly = true)
    public EffectivePermissionsResponse getEffectivePermissions(UUID userId) {
        Set<String> permissions = permissionEvaluator.getEffectivePermissions(userId);
        List<RoleAssignmentResponse> assignments = getUserRoleAssignments(userId);

        return EffectivePermissionsResponse.builder()
                .userId(userId)
                .permissions(new java.util.ArrayList<>(permissions))
                .roleAssignments(assignments)
                .build();
    }

    // ── Permission catalog ──────────────────────────────────────────────

    /**
     * List all available permissions in the system.
     *
     * @return list of all permissions
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::toPermissionResponse)
                .toList();
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private RoleEntity findRoleOrThrow(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
    }

    private Set<PermissionEntity> resolvePermissions(List<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }

        List<PermissionEntity> found = permissionRepository.findAllById(permissionIds);
        if (found.size() != permissionIds.size()) {
            Set<UUID> foundIds = found.stream()
                    .map(PermissionEntity::getId)
                    .collect(Collectors.toSet());
            Set<UUID> missing = permissionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new EntityNotFoundException("Permissions not found: " + missing);
        }

        return new HashSet<>(found);
    }

    private void invalidateCacheForRoleUsers(UUID roleId) {
        roleAssignmentRepository.findAll().stream()
                .filter(a -> a.getRole().getId().equals(roleId))
                .map(a -> a.getUser().getId())
                .distinct()
                .forEach(permissionEvaluator::invalidateCache);
    }

    private RoleResponse toRoleResponse(RoleEntity role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(this::toPermissionResponse)
                .toList();

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .scopeType(role.getScopeType())
                .isSystem(role.getIsSystem())
                .permissions(permissions)
                .createdAt(role.getCreatedAt())
                .build();
    }

    private RoleAssignmentResponse toRoleAssignmentResponse(RoleAssignmentEntity assignment) {
        return RoleAssignmentResponse.builder()
                .id(assignment.getId())
                .roleId(assignment.getRole().getId())
                .roleName(assignment.getRole().getName())
                .scopeType(assignment.getScopeType())
                .scopeId(assignment.getScopeId())
                .expiresAt(assignment.getExpiresAt())
                .assignedBy(assignment.getAssignedBy())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private PermissionResponse toPermissionResponse(PermissionEntity permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .resource(permission.getResource())
                .action(permission.getAction())
                .description(permission.getDescription())
                .build();
    }
}
