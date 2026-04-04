package com.gateway.identity.service;

import com.gateway.identity.dto.*;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.UserProfileEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.UserProfileRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UserEntity user;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = UserEntity.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("{argon2}hash")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(0)
                .build();

        profile = UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("User Name")
                .language("en")
                .timezone("UTC")
                .build();
    }

    // ── getProfile tests ────────────────────────────────────────────────

    @Test
    void getProfile_existingUser_returnsUserResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UserResponse result = userService.getProfile(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getProfile()).isNotNull();
        assertThat(result.getProfile().getDisplayName()).isEqualTo("User Name");
    }

    @Test
    void getProfile_noProfile_returnsUserWithNullProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        UserResponse result = userService.getProfile(userId);

        assertThat(result).isNotNull();
        assertThat(result.getProfile()).isNull();
    }

    @Test
    void getProfile_userNotFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(unknownId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── updateProfile tests ─────────────────────────────────────────────

    @Test
    void updateProfile_partialUpdate_appliesOnlyNonNullFields() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("New Name");
        request.setTimezone("America/New_York");

        UserResponse result = userService.updateProfile(userId, request);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("New Name");
        assertThat(saved.getTimezone()).isEqualTo("America/New_York");
        // Fields not set in request should remain unchanged
        assertThat(saved.getLanguage()).isEqualTo("en");
    }

    @Test
    void updateProfile_profileNotFound_throwsEntityNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("New Name");

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Profile not found");
    }

    @Test
    void updateProfile_allFields_updatesEverything() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Full Update");
        request.setPhone("+1234567890");
        request.setAvatarUrl("https://example.com/avatar.png");
        request.setBio("My bio");
        request.setTimezone("Europe/London");
        request.setLanguage("fr");
        request.setJobTitle("Engineer");
        request.setDepartment("Platform");

        userService.updateProfile(userId, request);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("Full Update");
        assertThat(saved.getPhone()).isEqualTo("+1234567890");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(saved.getBio()).isEqualTo("My bio");
        assertThat(saved.getTimezone()).isEqualTo("Europe/London");
        assertThat(saved.getLanguage()).isEqualTo("fr");
        assertThat(saved.getJobTitle()).isEqualTo("Engineer");
        assertThat(saved.getDepartment()).isEqualTo("Platform");
    }

    // ── getUserById tests ───────────────────────────────────────────────

    @Test
    void getUserById_existingUser_returnsResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UserResponse result = userService.getUserById(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
    }

    @Test
    void getUserById_notFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(unknownId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── listUsers tests ─────────────────────────────────────────────────

    @Test
    void listUsers_noFilters_returnsAll() {
        UserSearchRequest request = new UserSearchRequest();
        Page<UserEntity> page = new PageImpl<>(List.of(user));

        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        Page<UserResponse> result = userService.listUsers(request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void listUsers_withSearch_usesSearchQuery() {
        UserSearchRequest request = new UserSearchRequest();
        request.setSearch("user");
        Page<UserEntity> page = new PageImpl<>(List.of(user));

        when(userRepository.searchByEmailOrDisplayName(eq("user"), any(Pageable.class))).thenReturn(page);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        Page<UserResponse> result = userService.listUsers(request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(userRepository).searchByEmailOrDisplayName(eq("user"), any(Pageable.class));
    }

    @Test
    void listUsers_withStatusFilter_filtersByStatus() {
        UserSearchRequest request = new UserSearchRequest();
        request.setStatus("ACTIVE");
        Page<UserEntity> page = new PageImpl<>(List.of(user));

        when(userRepository.findByStatus(eq(UserStatus.ACTIVE), any(Pageable.class))).thenReturn(page);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        Page<UserResponse> result = userService.listUsers(request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(userRepository).findByStatus(eq(UserStatus.ACTIVE), any(Pageable.class));
    }

    // ── updateStatus tests ──────────────────────────────────────────────

    @Test
    void updateStatus_suspend_changesStatus() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(UserStatus.SUSPENDED);
        request.setReason("Policy violation");

        UserResponse result = userService.updateStatus(userId, request);

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        verify(auditService).logEvent(eq("user.status_changed"), eq("USER"),
                eq(userId.toString()), eq("SUCCESS"));
    }

    @Test
    void updateStatus_reactivate_clearsLockout() {
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(java.time.Instant.now());
        user.setFailedLoginCount(5);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(UserStatus.ACTIVE);

        userService.updateStatus(userId, request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getLockedUntil()).isNull();
        assertThat(saved.getFailedLoginCount()).isZero();
    }

    @Test
    void updateStatus_userNotFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> userService.updateStatus(unknownId, request))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
