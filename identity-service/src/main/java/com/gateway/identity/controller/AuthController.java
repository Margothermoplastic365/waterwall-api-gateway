package com.gateway.identity.controller;

import com.gateway.identity.dto.*;
import com.gateway.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints: registration, login,
 * email verification, and password reset.
 * <p>
 * All error responses are handled by the {@code GlobalExceptionHandler} and
 * returned as {@code ApiErrorResponse}.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user account.
     *
     * @param request the registration payload
     * @return 201 Created with the new user details
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate and obtain JWT tokens, or receive an MFA challenge.
     * <p>
     * Returns {@link LoginResponse} on success, or {@link MfaLoginResponse} when
     * MFA is required (status 200 in both cases — the client inspects the response body).
     *
     * @param request the login credentials
     * @return 200 OK with either full tokens or MFA challenge
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        Object response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify a user's email address via a token sent during registration.
     *
     * @param token the email verification token (query parameter)
     * @return 204 No Content on success
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Request a password reset email.
     * Always returns 204 regardless of whether the email exists (prevents user enumeration).
     *
     * @param request the forgot-password payload containing the email
     * @return 204 No Content
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset a user's password using a valid reset token.
     *
     * @param request the reset-password payload containing the token and new password
     * @return 204 No Content on success
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
