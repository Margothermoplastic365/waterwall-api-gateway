package com.gateway.identity.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.identity.dto.*;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private GatewayAuthentication createAuth() {
        return new GatewayAuthentication(
                USER_ID.toString(), "org-1", "user@test.com",
                List.of("ADMIN"), List.of("user:read", "user:suspend"),
                null, null);
    }

    // ── getProfile ──────────────────────────────────────────────────────

    @Test
    void getProfile_returnsOkWithUserResponse() {
        GatewayAuthentication auth = createAuth();
        UserResponse expected = UserResponse.builder()
                .id(USER_ID)
                .email("user@test.com")
                .emailVerified(true)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        when(userService.getProfile(USER_ID)).thenReturn(expected);

        ResponseEntity<UserResponse> response = userController.getProfile(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(userService).getProfile(USER_ID);
    }

    // ── updateProfile ───────────────────────────────────────────────────

    @Test
    void updateProfile_returnsOkWithUpdatedUser() {
        GatewayAuthentication auth = createAuth();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("New Name");

        UserResponse expected = UserResponse.builder()
                .id(USER_ID)
                .email("user@test.com")
                .status("ACTIVE")
                .build();

        when(userService.updateProfile(USER_ID, request)).thenReturn(expected);

        ResponseEntity<UserResponse> response = userController.updateProfile(auth, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(userService).updateProfile(USER_ID, request);
    }

    // ── listUsers ───────────────────────────────────────────────────────

    @Test
    void listUsers_returnsOkWithPage() {
        UserSearchRequest request = new UserSearchRequest();
        request.setSearch("alice");

        UserResponse user = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .build();
        Page<UserResponse> expectedPage = new PageImpl<>(List.of(user));

        when(userService.listUsers(request)).thenReturn(expectedPage);

        ResponseEntity<Page<UserResponse>> response = userController.listUsers(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expectedPage);
        verify(userService).listUsers(request);
    }

    // ── getUserById ─────────────────────────────────────────────────────

    @Test
    void getUserById_returnsOkWithUserResponse() {
        UUID targetId = UUID.randomUUID();
        UserResponse expected = UserResponse.builder()
                .id(targetId)
                .email("target@example.com")
                .status("ACTIVE")
                .build();

        when(userService.getUserById(targetId)).thenReturn(expected);

        ResponseEntity<UserResponse> response = userController.getUserById(targetId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(userService).getUserById(targetId);
    }

    // ── updateStatus ────────────────────────────────────────────────────

    @Test
    void updateStatus_returnsOkWithUpdatedUser() {
        UUID targetId = UUID.randomUUID();
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(UserStatus.SUSPENDED);
        request.setReason("Policy violation");

        UserResponse expected = UserResponse.builder()
                .id(targetId)
                .status("SUSPENDED")
                .build();

        when(userService.updateStatus(targetId, request)).thenReturn(expected);

        ResponseEntity<UserResponse> response = userController.updateStatus(targetId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(userService).updateStatus(targetId, request);
    }
}
