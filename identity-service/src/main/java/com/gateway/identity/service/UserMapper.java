package com.gateway.identity.service;

import com.gateway.identity.dto.ProfileResponse;
import com.gateway.identity.dto.UserResponse;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.UserProfileEntity;

/**
 * Maps entity objects to response DTOs.
 * Uses static methods for simplicity; can be replaced with a MapStruct interface
 * if mapping complexity grows.
 */
public final class UserMapper {

    private UserMapper() {
        // utility class — no instantiation
    }

    /**
     * Maps a {@link UserEntity} and its associated {@link UserProfileEntity}
     * to a {@link UserResponse}.
     *
     * @param user    the user entity (must not be null)
     * @param profile the profile entity (may be null)
     * @return a fully populated UserResponse
     */
    public static UserResponse toResponse(UserEntity user, UserProfileEntity profile) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .profile(profile != null ? toProfileResponse(profile) : null)
                .build();
    }

    /**
     * Maps a {@link UserProfileEntity} to a {@link ProfileResponse}.
     *
     * @param profile the profile entity (must not be null)
     * @return a fully populated ProfileResponse
     */
    public static ProfileResponse toProfileResponse(UserProfileEntity profile) {
        return ProfileResponse.builder()
                .displayName(profile.getDisplayName())
                .phone(profile.getPhone())
                .phoneVerified(Boolean.TRUE.equals(profile.getPhoneVerified()))
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .timezone(profile.getTimezone())
                .language(profile.getLanguage())
                .jobTitle(profile.getJobTitle())
                .department(profile.getDepartment())
                .build();
    }
}
