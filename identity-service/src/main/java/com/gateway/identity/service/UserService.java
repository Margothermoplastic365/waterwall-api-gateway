package com.gateway.identity.service;

import com.gateway.identity.dto.*;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.UserProfileEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.UserProfileRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for user profile management and admin user operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;

    // ── Profile operations (self-service) ───────────────────────────────

    /**
     * Get the authenticated user's profile.
     *
     * @param userId the authenticated user's ID
     * @return the user response with profile information
     * @throws EntityNotFoundException if the user is not found
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        UserEntity user = findUserOrThrow(userId);
        UserProfileEntity profile = userProfileRepository.findByUserId(userId).orElse(null);
        return UserMapper.toResponse(user, profile);
    }

    /**
     * Partially update the authenticated user's profile.
     * Only non-null fields in the request are applied.
     *
     * @param userId  the authenticated user's ID
     * @param request the update profile request
     * @return the updated user response
     * @throws EntityNotFoundException if the user or profile is not found
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserEntity user = findUserOrThrow(userId);
        UserProfileEntity profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Profile not found for user: " + userId));

        // Partial update — only apply non-null fields
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getTimezone() != null) {
            profile.setTimezone(request.getTimezone());
        }
        if (request.getLanguage() != null) {
            profile.setLanguage(request.getLanguage());
        }
        if (request.getJobTitle() != null) {
            profile.setJobTitle(request.getJobTitle());
        }
        if (request.getDepartment() != null) {
            profile.setDepartment(request.getDepartment());
        }

        profile = userProfileRepository.save(profile);

        log.info("Profile updated for user: id={}", userId);
        return UserMapper.toResponse(user, profile);
    }

    // ── Admin operations ────────────────────────────────────────────────

    /**
     * List users with search and filtering. Admin-only operation.
     *
     * @param request the search/filter criteria including pagination
     * @return a page of user responses
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(UserSearchRequest request) {
        Sort sort = "asc".equalsIgnoreCase(request.getSortDir())
                ? Sort.by(request.getSortBy()).ascending()
                : Sort.by(request.getSortBy()).descending();

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<UserEntity> userPage;

        if (request.getSearch() != null && !request.getSearch().isBlank()) {
            // Search by email or display name
            userPage = userRepository.searchByEmailOrDisplayName(request.getSearch(), pageable);
        } else if (request.getStatus() != null && !request.getStatus().isBlank()) {
            // Filter by status
            UserStatus status = UserStatus.valueOf(request.getStatus().toUpperCase());
            userPage = userRepository.findByStatus(status, pageable);
        } else {
            // Return all
            userPage = userRepository.findAll(pageable);
        }

        return userPage.map(user -> {
            UserProfileEntity profile = userProfileRepository.findByUserId(user.getId())
                    .orElse(null);
            return UserMapper.toResponse(user, profile);
        });
    }

    /**
     * Get any user by ID. Admin-only operation.
     *
     * @param userId the target user's ID
     * @return the user response
     * @throws EntityNotFoundException if the user is not found
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserEntity user = findUserOrThrow(userId);
        UserProfileEntity profile = userProfileRepository.findByUserId(userId).orElse(null);
        return UserMapper.toResponse(user, profile);
    }

    /**
     * Update a user's status (activate, suspend, lock, deactivate). Admin-only operation.
     *
     * @param userId  the target user's ID
     * @param request the status update request containing the new status and optional reason
     * @return the updated user response
     * @throws EntityNotFoundException if the user is not found
     */
    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateStatusRequest request) {
        UserEntity user = findUserOrThrow(userId);

        UserStatus previousStatus = user.getStatus();
        user.setStatus(request.getStatus());

        // Clear lockout if reactivating
        if (request.getStatus() == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }

        user = userRepository.save(user);

        UserProfileEntity profile = userProfileRepository.findByUserId(userId).orElse(null);

        auditService.logEvent(
                "user.status_changed",
                "USER",
                userId.toString(),
                "SUCCESS"
        );

        log.info("User status changed: id={}, from={}, to={}, reason={}",
                userId, previousStatus, request.getStatus(), request.getReason());

        return UserMapper.toResponse(user, profile);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private UserEntity findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}
