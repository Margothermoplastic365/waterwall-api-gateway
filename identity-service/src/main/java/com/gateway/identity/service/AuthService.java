package com.gateway.identity.service;

import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.identity.dto.*;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.UserProfileEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.gateway.identity.repository.UserProfileRepository;
import com.gateway.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Core authentication service handling registration, login, email verification,
 * and password reset flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final int EMAIL_VERIFY_TOKEN_HOURS = 24;
    private static final int PASSWORD_RESET_TOKEN_HOURS = 1;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PasswordPolicyService passwordPolicyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final MfaService mfaService;

    // ── Registration ────────────────────────────────────────────────────

    /**
     * Register a new user account.
     *
     * @param request the registration request containing email, password, and display name
     * @return the newly created user response
     * @throws IllegalArgumentException  if the email is already taken
     * @throws PasswordPolicyException   if the password does not meet policy requirements
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new IllegalArgumentException("Email address is already registered");
        }

        // Validate password against policy (throws PasswordPolicyException on failure)
        passwordPolicyService.validate(request.getPassword());

        // Create user entity
        String emailVerifyToken = UUID.randomUUID().toString();
        UserEntity user = UserEntity.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordPolicyService.encode(request.getPassword()))
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .emailVerifyToken(emailVerifyToken)
                .emailVerifyExpiresAt(Instant.now().plus(EMAIL_VERIFY_TOKEN_HOURS, ChronoUnit.HOURS))
                .failedLoginCount(0)
                .build();
        user = userRepository.save(user);

        // Create user profile
        UserProfileEntity profile = UserProfileEntity.builder()
                .user(user)
                .displayName(request.getDisplayName())
                .phoneVerified(false)
                .language("en")
                .timezone("UTC")
                .build();
        profile = userProfileRepository.save(profile);

        // Publish domain event
        publishUserEvent("user.registered", user.getId().toString());

        // TODO: Send verification email via notification-service
        log.info("User registered: email={}, id={}", user.getEmail(), user.getId());

        return UserMapper.toResponse(user, profile);
    }

    // ── Email verification ──────────────────────────────────────────────

    /**
     * Verify a user's email address using the verification token.
     *
     * @param token the email verification token
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    @Transactional
    public void verifyEmail(String token) {
        UserEntity user = userRepository.findByEmailVerifyToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        if (user.getEmailVerifyExpiresAt() != null
                && user.getEmailVerifyExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifyToken(null);
        user.setEmailVerifyExpiresAt(null);
        userRepository.save(user);

        auditService.logEvent("user.email_verified", "USER",
                user.getId().toString(), "SUCCESS",
                user.getEmail(), user.getId());

        log.info("Email verified for user: id={}", user.getId());
    }

    // ── Login ───────────────────────────────────────────────────────────

    /**
     * Authenticate a user and return JWT tokens, or an MFA challenge if MFA is enabled.
     * <p>
     * When MFA is required the method returns an {@link MfaLoginResponse} containing a
     * temporary session token. The client must complete the MFA challenge and call
     * {@link #completeMfaLogin} to obtain full JWT tokens.
     *
     * @param request the login request containing email and password
     * @return {@link LoginResponse} with JWT tokens, or {@link MfaLoginResponse} if MFA is required
     * @throws BadCredentialsException if credentials are invalid or account is locked/inactive
     */
    @Transactional
    public Object login(LoginRequest request) {
        // Find user by email
        UserEntity user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is locked
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            auditService.logEvent("user.login_attempt_locked", "USER",
                    user.getId().toString(), "FAILURE",
                    user.getEmail(), user.getId());
            throw new BadCredentialsException("Account is temporarily locked. Please try again later.");
        }

        // Check if account is active
        if (user.getStatus() != UserStatus.ACTIVE
                && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            auditService.logEvent("user.login_attempt_inactive", "USER",
                    user.getId().toString(), "FAILURE",
                    user.getEmail(), user.getId());
            throw new BadCredentialsException("Account is not active");
        }

        // Verify password using PasswordPolicyService's delegating encoder
        if (!passwordPolicyService.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Successful password verification — reset failed count and update last login
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Check if user has any enabled MFA factor
        List<String> enabledFactors = mfaService.getEnabledFactors(user.getId());
        if (!enabledFactors.isEmpty()) {
            // Generate a temporary MFA session token (short-lived, carries user ID)
            String mfaSessionToken = jwtTokenProvider.generateMfaSessionToken(user);

            auditService.logEvent("user.login.mfa_required", "USER",
                    user.getId().toString(), "PENDING",
                    user.getEmail(), user.getId());

            log.info("MFA required for user: id={}, factors={}", user.getId(), enabledFactors);

            return MfaLoginResponse.builder()
                    .mfaRequired(true)
                    .mfaSessionToken(mfaSessionToken)
                    .availableFactors(enabledFactors)
                    .build();
        }

        // No MFA — issue full tokens
        return issueFullLoginResponse(user);
    }

    /**
     * Complete MFA login after successful second-factor verification.
     * Called after the client verifies TOTP or email OTP using the MFA session token.
     *
     * @param userId the user ID extracted from the MFA session token
     * @return full login response with JWT tokens
     */
    @Transactional
    public LoginResponse completeMfaLogin(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        auditService.logEvent("user.login.mfa_completed", "USER",
                user.getId().toString(), "SUCCESS",
                user.getEmail(), user.getId());

        return issueFullLoginResponse(user);
    }

    /**
     * Issue the full login response with JWT tokens for the given user.
     */
    private LoginResponse issueFullLoginResponse(UserEntity user) {
        // Fetch user's active role assignments
        var assignments = roleAssignmentRepository.findActiveByUserId(user.getId());

        List<String> roles = assignments.stream()
                .map(ra -> ra.getRole().getName())
                .distinct()
                .toList();

        // Flatten all permissions from all assigned roles
        List<String> permissions = assignments.stream()
                .flatMap(ra -> ra.getRole().getPermissions().stream())
                .map(p -> p.getResource() + ":" + p.getAction())
                .distinct()
                .toList();

        // Generate tokens (access token includes roles AND permissions)
        String accessToken = jwtTokenProvider.generateAccessToken(user, roles, permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Load profile for the response
        UserProfileEntity profile = userProfileRepository.findByUserId(user.getId())
                .orElse(null);

        auditService.logEvent("user.login", "USER",
                user.getId().toString(), "SUCCESS",
                user.getEmail(), user.getId());

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenTtlSeconds())
                .user(UserMapper.toResponse(user, profile))
                .build();
    }

    // ── Forgot password ─────────────────────────────────────────────────

    /**
     * Initiate password reset flow by generating a reset token.
     * Silently returns if the email is not found to prevent user enumeration.
     *
     * @param email the user's email address
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            // Re-use emailVerifyToken/emailVerifyExpiresAt fields for password reset.
            // TODO: Add dedicated passwordResetToken and passwordResetExpiresAt columns
            //       to UserEntity for proper separation of concerns.
            String resetToken = UUID.randomUUID().toString();
            user.setEmailVerifyToken(resetToken);
            user.setEmailVerifyExpiresAt(
                    Instant.now().plus(PASSWORD_RESET_TOKEN_HOURS, ChronoUnit.HOURS));
            userRepository.save(user);

            auditService.logEvent("user.forgot_password", "USER",
                    user.getId().toString(), "SUCCESS",
                    user.getEmail(), user.getId());

            // TODO: Send password reset email via notification-service
            log.info("Password reset token generated for user: id={}", user.getId());
        });
    }

    // ── Reset password ──────────────────────────────────────────────────

    /**
     * Reset a user's password using a valid reset token.
     *
     * @param request the reset password request containing the token and new password
     * @throws IllegalArgumentException if the token is invalid or expired
     * @throws PasswordPolicyException  if the new password does not meet policy requirements
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Find user by reset token (using emailVerifyToken field)
        UserEntity user = userRepository.findByEmailVerifyToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        // Check token expiry
        if (user.getEmailVerifyExpiresAt() != null
                && user.getEmailVerifyExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        // Validate new password against policy
        passwordPolicyService.validate(request.getNewPassword());

        // Update password
        user.setPasswordHash(passwordPolicyService.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setEmailVerifyToken(null);
        user.setEmailVerifyExpiresAt(null);
        // Reset any lockout state
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditService.logEvent("user.password_reset", "USER",
                user.getId().toString(), "SUCCESS",
                user.getEmail(), user.getId());

        // TODO: Invalidate all existing sessions for this user

        log.info("Password reset completed for user: id={}", user.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void handleFailedLogin(UserEntity user) {
        int failedCount = (user.getFailedLoginCount() != null
                ? user.getFailedLoginCount() : 0) + 1;
        user.setFailedLoginCount(failedCount);

        if (failedCount >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(
                    Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
            user.setStatus(UserStatus.LOCKED);
            log.warn("Account locked due to {} failed login attempts: userId={}",
                    failedCount, user.getId());
        }

        userRepository.save(user);

        auditService.logEvent("user.login_failed", "USER",
                user.getId().toString(), "FAILURE",
                user.getEmail(), user.getId());
    }

    private void publishUserEvent(String eventType, String userId) {
        try {
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .eventType(eventType)
                    .actorId(userId)
                    .build();
            eventPublisher.publish(RabbitMQExchanges.PLATFORM_EVENTS, eventType, event);
        } catch (Exception ex) {
            // Event publishing failure must not break registration
            log.error("Failed to publish event: type={}, userId={}", eventType, userId, ex);
        }
    }

    // ── Inner event class ───────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class UserRegisteredEvent extends BaseEvent {
    }
}
