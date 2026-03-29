package com.gateway.identity.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.auth.RequiresPermission;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for user profile management and admin user operations.
 * <p>
 * Self-service endpoints ({@code /me}) use the authenticated principal's ID.
 * Admin endpoints require specific permissions enforced via {@link RequiresPermission}.
 * All error responses are handled by the {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Self-service endpoints ──────────────────────────────────────────

    /**
     * Get the authenticated user's own profile.
     *
     * @param authentication the current authentication token
     * @return 200 OK with the user's profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(GatewayAuthentication authentication) {
        UUID userId = UUID.fromString(authentication.getUserId());
        UserResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update the authenticated user's own profile.
     * Only non-null fields in the request body are applied (partial update).
     *
     * @param authentication the current authentication token
     * @param request        the profile update payload
     * @return 200 OK with the updated user profile
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            GatewayAuthentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(authentication.getUserId());
        UserResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    // ── Admin endpoints ─────────────────────────────────────────────────

    /**
     * List all users with optional search, filter, and pagination.
     * Requires the {@code user:read} permission.
     *
     * @param request search/filter/pagination criteria
     * @return 200 OK with a page of user responses
     */
    @GetMapping
    @RequiresPermission("user:read")
    public ResponseEntity<Page<UserResponse>> listUsers(@ModelAttribute UserSearchRequest request) {
        Page<UserResponse> page = userService.listUsers(request);
        return ResponseEntity.ok(page);
    }

    /**
     * Get any user by ID. Requires the {@code user:read} permission.
     *
     * @param id the target user's UUID
     * @return 200 OK with the user details
     */
    @GetMapping("/{id}")
    @RequiresPermission("user:read")
    public ResponseEntity<UserResponse> getUserById(@PathVariable("id") UUID id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update a user's account status (activate, suspend, lock, deactivate).
     * Requires the {@code user:suspend} permission.
     *
     * @param id      the target user's UUID
     * @param request the status update payload
     * @return 200 OK with the updated user details
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("user:suspend")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        UserResponse response = userService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
