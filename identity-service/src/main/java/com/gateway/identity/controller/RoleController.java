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
 * REST controller for role and permission catalog management.
 * <p>
 * Role mutation endpoints require the {@code role:create} permission.
 * Read-only endpoints are accessible to any authenticated user.
 */
@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RbacService rbacService;

    /**
     * Create a new role.
     *
     * @param request the role creation payload
     * @return 201 Created with the new role details
     */
    @PostMapping
    @RequiresPermission("role:create")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = rbacService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all roles.
     *
     * @return 200 OK with a list of all roles
     */
    @GetMapping
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = rbacService.listRoles();
        return ResponseEntity.ok(roles);
    }

    /**
     * Get a role by ID.
     *
     * @param id the role UUID
     * @return 200 OK with the role details
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> getRole(@PathVariable("id") UUID id) {
        RoleResponse response = rbacService.getRole(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing role.
     *
     * @param id      the role UUID
     * @param request the update payload
     * @return 200 OK with the updated role details
     */
    @PutMapping("/{id}")
    @RequiresPermission("role:create")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = rbacService.updateRole(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a role.
     *
     * @param id the role UUID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("role:create")
    public ResponseEntity<Void> deleteRole(@PathVariable("id") UUID id) {
        rbacService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List all available permissions in the system.
     *
     * @return 200 OK with a list of all permissions
     */
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        List<PermissionResponse> permissions = rbacService.listPermissions();
        return ResponseEntity.ok(permissions);
    }
}
