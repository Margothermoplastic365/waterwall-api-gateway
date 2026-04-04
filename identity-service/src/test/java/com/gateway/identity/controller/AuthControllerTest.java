package com.gateway.identity.controller;

import com.gateway.identity.dto.*;
import com.gateway.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    // ── register ────────────────────────────────────────────────────────

    @Test
    void register_returnsCreatedWithUserResponse() {
        RegisterRequest request = RegisterRequest.builder()
                .email("alice@example.com")
                .password("Str0ngP@ss!")
                .displayName("Alice")
                .build();

        UserResponse expected = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .emailVerified(false)
                .status("PENDING_VERIFICATION")
                .createdAt(Instant.now())
                .build();

        when(authService.register(request)).thenReturn(expected);

        ResponseEntity<UserResponse> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(authService).register(request);
        verifyNoMoreInteractions(authService);
    }

    // ── login ───────────────────────────────────────────────────────────

    @Test
    void login_returnsOkWithLoginResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Str0ngP@ss!");

        LoginResponse expected = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();

        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<Object> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(authService).login(request);
    }

    @Test
    void login_returnsOkWithMfaLoginResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Str0ngP@ss!");

        MfaLoginResponse mfaResponse = MfaLoginResponse.builder()
                .mfaRequired(true)
                .mfaSessionToken("mfa-session-token")
                .build();

        when(authService.login(request)).thenReturn(mfaResponse);

        ResponseEntity<Object> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(MfaLoginResponse.class);
        verify(authService).login(request);
    }

    // ── verifyEmail ─────────────────────────────────────────────────────

    @Test
    void verifyEmail_returnsNoContent() {
        String token = "verification-token-123";

        ResponseEntity<Void> response = authController.verifyEmail(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(authService).verifyEmail(token);
    }

    // ── forgotPassword ──────────────────────────────────────────────────

    @Test
    void forgotPassword_returnsNoContent() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        ResponseEntity<Void> response = authController.forgotPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(authService).forgotPassword("alice@example.com");
    }

    // ── resetPassword ───────────────────────────────────────────────────

    @Test
    void resetPassword_returnsNoContent() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token-456");
        request.setNewPassword("N3wP@ssword!");

        ResponseEntity<Void> response = authController.resetPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(authService).resetPassword(request);
    }
}
