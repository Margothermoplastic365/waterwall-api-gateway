package com.gateway.identity.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.ApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID APP_ID = UUID.randomUUID();

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ApplicationController applicationController;

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                USER_ID.toString(), "org-1", "dev@test.com",
                List.of("DEVELOPER"), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ApplicationResponse sampleApp() {
        return ApplicationResponse.builder()
                .id(APP_ID)
                .name("My App")
                .description("Test application")
                .callbackUrls(List.of("https://example.com/callback"))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    // ── createApplication ───────────────────────────────────────────────

    @Test
    void createApplication_returnsCreated() {
        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .name("My App")
                .description("Test application")
                .callbackUrls(List.of("https://example.com/callback"))
                .build();

        ApplicationResponse expected = sampleApp();
        when(applicationService.createApplication(USER_ID, request)).thenReturn(expected);

        ResponseEntity<ApplicationResponse> response =
                applicationController.createApplication(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(applicationService).createApplication(USER_ID, request);
    }

    // ── listMyApplications ──────────────────────────────────────────────

    @Test
    void listMyApplications_returnsOkWithList() {
        List<ApplicationResponse> expected = List.of(sampleApp());
        when(applicationService.listMyApplications(USER_ID)).thenReturn(expected);

        ResponseEntity<List<ApplicationResponse>> response =
                applicationController.listMyApplications();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(applicationService).listMyApplications(USER_ID);
    }

    // ── getApplication ──────────────────────────────────────────────────

    @Test
    void getApplication_returnsOkWithApp() {
        ApplicationResponse expected = sampleApp();
        when(applicationService.getApplication(APP_ID)).thenReturn(expected);

        ResponseEntity<ApplicationResponse> response =
                applicationController.getApplication(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(applicationService).getApplication(APP_ID);
    }

    // ── updateApplication ───────────────────────────────────────────────

    @Test
    void updateApplication_returnsOkWithUpdatedApp() {
        UpdateApplicationRequest request = new UpdateApplicationRequest();
        request.setName("Updated App");

        ApplicationResponse expected = sampleApp();
        when(applicationService.updateApplication(APP_ID, USER_ID, request)).thenReturn(expected);

        ResponseEntity<ApplicationResponse> response =
                applicationController.updateApplication(APP_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(applicationService).updateApplication(APP_ID, USER_ID, request);
    }

    // ── deleteApplication ───────────────────────────────────────────────

    @Test
    void deleteApplication_returnsNoContent() {
        ResponseEntity<Void> response = applicationController.deleteApplication(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(applicationService).deleteApplication(APP_ID, USER_ID);
    }

    // ── generateBasicAuth ───────────────────────────────────────────────

    @Test
    void generateBasicAuth_returnsOkWithSecret() {
        BasicAuthSecretResponse expected = BasicAuthSecretResponse.builder()
                .clientId("client-id-123")
                .clientSecret("client-secret-456")
                .build();

        when(applicationService.generateBasicAuthSecret(APP_ID, USER_ID)).thenReturn(expected);

        ResponseEntity<BasicAuthSecretResponse> response =
                applicationController.generateBasicAuth(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(applicationService).generateBasicAuthSecret(APP_ID, USER_ID);
    }

    // ── getBasicAuthStatus ──────────────────────────────────────────────

    @Test
    void getBasicAuthStatus_returnsOkWithConfiguredTrue() {
        when(applicationService.hasBasicAuth(APP_ID)).thenReturn(true);

        ResponseEntity<Map<String, Boolean>> response =
                applicationController.getBasicAuthStatus(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("configured", true);
        verify(applicationService).hasBasicAuth(APP_ID);
    }

    @Test
    void getBasicAuthStatus_returnsOkWithConfiguredFalse() {
        when(applicationService.hasBasicAuth(APP_ID)).thenReturn(false);

        ResponseEntity<Map<String, Boolean>> response =
                applicationController.getBasicAuthStatus(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("configured", false);
    }

    // ── revokeBasicAuth ─────────────────────────────────────────────────

    @Test
    void revokeBasicAuth_returnsNoContent() {
        ResponseEntity<Void> response = applicationController.revokeBasicAuth(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(applicationService).revokeBasicAuth(APP_ID, USER_ID);
    }
}
