package com.gateway.identity.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.ApplicationService;
import com.gateway.identity.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for application (client) management.
 * <p>
 * Application management is self-service: authenticated developers can create,
 * list, update, and delete their own applications. No special permissions are
 * required beyond being authenticated.
 * <p>
 * All error responses are handled by the {@code GlobalExceptionHandler} and
 * returned as {@code ApiErrorResponse}.
 */
@RestController
@RequestMapping("/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * Create a new application for the authenticated user.
     *
     * @param request the application creation payload
     * @return 201 Created with the new application details
     */
    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request) {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        ApplicationResponse response = applicationService.createApplication(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all applications owned by the authenticated user.
     *
     * @return 200 OK with the list of applications
     */
    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listMyApplications() {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        List<ApplicationResponse> responses = applicationService.listMyApplications(userId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get a single application by ID.
     *
     * @param id the application UUID
     * @return 200 OK with the application details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable("id") UUID id) {
        ApplicationResponse response = applicationService.getApplication(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing application owned by the authenticated user.
     *
     * @param id      the application UUID
     * @param request the update payload
     * @return 200 OK with the updated application details
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateApplicationRequest request) {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        ApplicationResponse response = applicationService.updateApplication(id, userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an application owned by the authenticated user.
     *
     * @param id the application UUID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        applicationService.deleteApplication(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generate (or regenerate) Basic Auth credentials for an application.
     * The plaintext secret is returned only once.
     *
     * @param id the application UUID
     * @return 200 OK with clientId and clientSecret
     */
    @PostMapping("/{id}/basic-auth")
    public ResponseEntity<BasicAuthSecretResponse> generateBasicAuth(@PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        BasicAuthSecretResponse response = applicationService.generateBasicAuthSecret(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Check whether Basic Auth is configured for an application.
     *
     * @param id the application UUID
     * @return 200 OK with configured status
     */
    @GetMapping("/{id}/basic-auth")
    public ResponseEntity<Map<String, Boolean>> getBasicAuthStatus(@PathVariable("id") UUID id) {
        boolean configured = applicationService.hasBasicAuth(id);
        return ResponseEntity.ok(Collections.singletonMap("configured", configured));
    }

    /**
     * Revoke Basic Auth credentials for an application.
     *
     * @param id the application UUID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}/basic-auth")
    public ResponseEntity<Void> revokeBasicAuth(@PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(SecurityContextHelper.getCurrentUserId());
        applicationService.revokeBasicAuth(id, userId);
        return ResponseEntity.noContent().build();
    }
}
