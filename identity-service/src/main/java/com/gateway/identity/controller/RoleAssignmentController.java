package com.gateway.identity.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.RbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing role assignments for a specific user.
 * <p>
 * All endpoints are scoped under {@code /v1/users/{userId}/roles} and require
 * appropriate RBAC permissions.
 */
@RestController
@RequestMapping("/v1/users/{userId}/roles")
@RequiredArgsConstructor
public class RoleAssignmentController {

    private final RbacService rbacService;

    /**
     * Assign a role to a user.
     *
     * @param userId  the target user UUID
     * @param request the role assignment payload
     * @return 201 Created with the new assignment details
     */
    @PostMapping
    @RequiresPermission("role:assign")
    public ResponseEntity<RoleAssignmentResponse> assignRole(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        RoleAssignmentResponse response = rbacService.assignRole(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Revoke a role assignment from a user.
     *
     * @param userId       the user UUID that owns the assignment
     * @param assignmentId the assignment UUID to revoke
     * @return 204 No Content on success
     */
    @DeleteMapping("/{assignmentId}")
    @RequiresPermission("role:revoke")
    public ResponseEntity<Void> revokeRole(
            @PathVariable("userId") UUID userId,
            @PathVariable("assignmentId") UUID assignmentId) {
        rbacService.revokeRole(userId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all active role assignments for a user.
     *
     * @param userId the target user UUID
     * @return 200 OK with the list of active role assignments
     */
    @GetMapping
    @RequiresPermission("role:read")
    public ResponseEntity<List<RoleAssignmentResponse>> getUserRoleAssignments(
            @PathVariable("userId") UUID userId) {
        List<RoleAssignmentResponse> assignments = rbacService.getUserRoleAssignments(userId);
        return ResponseEntity.ok(assignments);
    }

    /**
     * Get the effective permissions for a user, combining all role assignments.
     *
     * @param userId the target user UUID
     * @return 200 OK with the effective permissions response
     */
    @GetMapping("/permissions")
    @RequiresPermission("role:read")
    public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(
            @PathVariable("userId") UUID userId) {
        EffectivePermissionsResponse response = rbacService.getEffectivePermissions(userId);
        return ResponseEntity.ok(response);
    }
}
