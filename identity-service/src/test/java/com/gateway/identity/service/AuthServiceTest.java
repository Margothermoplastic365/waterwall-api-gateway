package com.gateway.identity.service;

import com.gateway.common.events.EventPublisher;
import com.gateway.identity.dto.*;
import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.RoleEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.UserProfileEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.gateway.identity.repository.UserProfileRepository;
import com.gateway.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private RoleAssignmentRepository roleAssignmentRepository;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private EventPublisher eventPublisher;
    @Mock private AuditService auditService;
    @Mock private MfaService mfaService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private UserEntity activeUser;
    private UserProfileEntity userProfile;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .email("test@example.com")
                .password("StrongP@ss1")
                .displayName("Test User")
                .build();

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("StrongP@ss1");

        activeUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("{argon2}encodedHash")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(0)
                .build();

        userProfile = UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .user(activeUser)
                .displayName("Test User")
                .language("en")
                .timezone("UTC")
                .build();
    }

    // ── Registration tests ──────────────────────────────────────────────

    @Test
    void register_success_createsUserAndProfile() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordPolicyService.encode("StrongP@ss1")).thenReturn("{argon2}encodedHash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = authService.register(registerRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getStatus()).isEqualTo("PENDING_VERIFICATION");

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("{argon2}encodedHash");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getEmailVerified()).isFalse();
        assertThat(saved.getEmailVerifyToken()).isNotNull();
        assertThat(saved.getFailedLoginCount()).isZero();

        ArgumentCaptor<UserProfileEntity> profileCaptor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getDisplayName()).isEqualTo("Test User");

        verify(passwordPolicyService).validate("StrongP@ss1");
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_weakPassword_throwsPasswordPolicyException() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        doThrow(new PasswordPolicyException(List.of("Too short")))
                .when(passwordPolicyService).validate("StrongP@ss1");

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(PasswordPolicyException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailIsTrimmedAndLowercased() {
        RegisterRequest req = RegisterRequest.builder()
                .email("  Test@Example.COM  ")
                .password("StrongP@ss1")
                .displayName("Test")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordPolicyService.encode(any())).thenReturn("{argon2}hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(req);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
    }

    // ── Login tests ─────────────────────────────────────────────────────

    @Test
    void login_success_noMfa_returnsLoginResponse() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.matches("StrongP@ss1", "{argon2}encodedHash")).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);
        when(mfaService.getEnabledFactors(activeUser.getId())).thenReturn(Collections.emptyList());
        when(roleAssignmentRepository.findActiveByUserId(activeUser.getId())).thenReturn(Collections.emptyList());
        when(jwtTokenProvider.generateAccessToken(eq(activeUser), anyList(), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(activeUser)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(userProfileRepository.findByUserId(activeUser.getId())).thenReturn(Optional.of(userProfile));

        Object result = authService.login(loginRequest);

        assertThat(result).isInstanceOf(LoginResponse.class);
        LoginResponse loginResponse = (LoginResponse) result;
        assertThat(loginResponse.getAccessToken()).isEqualTo("access-token");
        assertThat(loginResponse.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(loginResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(loginResponse.getExpiresIn()).isEqualTo(1800L);
        assertThat(loginResponse.getUser()).isNotNull();
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.matches("StrongP@ss1", "{argon2}encodedHash")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_accountLocked_throwsBadCredentials() {
        activeUser.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("temporarily locked");
    }

    @Test
    void login_suspendedAccount_throwsBadCredentials() {
        activeUser.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void login_mfaRequired_returnsMfaLoginResponse() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.matches("StrongP@ss1", "{argon2}encodedHash")).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);
        when(mfaService.getEnabledFactors(activeUser.getId())).thenReturn(List.of("TOTP"));
        when(jwtTokenProvider.generateMfaSessionToken(activeUser)).thenReturn("mfa-session-token");

        Object result = authService.login(loginRequest);

        assertThat(result).isInstanceOf(MfaLoginResponse.class);
        MfaLoginResponse mfaResponse = (MfaLoginResponse) result;
        assertThat(mfaResponse.isMfaRequired()).isTrue();
        assertThat(mfaResponse.getMfaSessionToken()).isEqualTo("mfa-session-token");
        assertThat(mfaResponse.getAvailableFactors()).containsExactly("TOTP");
    }

    @Test
    void login_wrongPassword_incrementsFailedCount() {
        activeUser.setFailedLoginCount(3);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.matches("StrongP@ss1", "{argon2}encodedHash")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginCount()).isEqualTo(4);
    }

    @Test
    void login_fifthFailedAttempt_locksAccount() {
        activeUser.setFailedLoginCount(4);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.matches("StrongP@ss1", "{argon2}encodedHash")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getFailedLoginCount()).isEqualTo(5);
        assertThat(saved.getLockedUntil()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    // ── Email verification tests ────────────────────────────────────────

    @Test
    void verifyEmail_validToken_activatesUser() {
        UserEntity pendingUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .emailVerified(false)
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerifyToken("valid-token")
                .emailVerifyExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
                .build();

        when(userRepository.findByEmailVerifyToken("valid-token")).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(pendingUser);

        authService.verifyEmail("valid-token");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEmailVerifyToken()).isNull();
        assertThat(saved.getEmailVerifyExpiresAt()).isNull();
    }

    @Test
    void verifyEmail_invalidToken_throwsIllegalArgument() {
        when(userRepository.findByEmailVerifyToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyEmail_expiredToken_throwsIllegalArgument() {
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .emailVerifyToken("expired-token")
                .emailVerifyExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(userRepository.findByEmailVerifyToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    // ── Forgot password tests ───────────────────────────────────────────

    @Test
    void forgotPassword_existingEmail_setsResetToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);

        authService.forgotPassword("test@example.com");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getEmailVerifyToken()).isNotNull();
        assertThat(saved.getEmailVerifyExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void forgotPassword_unknownEmail_silentlyReturns() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("unknown@example.com");

        verify(userRepository, never()).save(any());
    }

    // ── Reset password tests ────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesPassword() {
        activeUser.setEmailVerifyToken("reset-token");
        activeUser.setEmailVerifyExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        activeUser.setFailedLoginCount(3);
        activeUser.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewStr0ng!Pass");

        when(userRepository.findByEmailVerifyToken("reset-token")).thenReturn(Optional.of(activeUser));
        when(passwordPolicyService.encode("NewStr0ng!Pass")).thenReturn("{argon2}newHash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(activeUser);

        authService.resetPassword(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("{argon2}newHash");
        assertThat(saved.getPasswordChangedAt()).isNotNull();
        assertThat(saved.getEmailVerifyToken()).isNull();
        assertThat(saved.getFailedLoginCount()).isZero();
        assertThat(saved.getLockedUntil()).isNull();

        verify(passwordPolicyService).validate("NewStr0ng!Pass");
    }

    @Test
    void resetPassword_invalidToken_throwsIllegalArgument() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("bad-token");
        request.setNewPassword("NewStr0ng!Pass");

        when(userRepository.findByEmailVerifyToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void resetPassword_expiredToken_throwsIllegalArgument() {
        activeUser.setEmailVerifyToken("expired-reset");
        activeUser.setEmailVerifyExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("expired-reset");
        request.setNewPassword("NewStr0ng!Pass");

        when(userRepository.findByEmailVerifyToken("expired-reset")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    // ── completeMfaLogin tests ──────────────────────────────────────────

    @Test
    void completeMfaLogin_success_returnsLoginResponse() {
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(roleAssignmentRepository.findActiveByUserId(activeUser.getId())).thenReturn(Collections.emptyList());
        when(jwtTokenProvider.generateAccessToken(eq(activeUser), anyList(), anyList())).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(activeUser)).thenReturn("refresh");
        when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(userProfileRepository.findByUserId(activeUser.getId())).thenReturn(Optional.of(userProfile));

        LoginResponse result = authService.completeMfaLogin(activeUser.getId());

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void completeMfaLogin_userNotFound_throwsBadCredentials() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeMfaLogin(unknownId))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("User not found");
    }
}
