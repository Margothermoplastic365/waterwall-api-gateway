package com.gateway.identity.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for API key management within an application.
 * <p>
 * API keys are scoped to an application and managed by the application owner.
 * The full key value is only returned once at creation time (or on rotation);
 * subsequent list operations return a masked prefix only.
 * <p>
 * All error responses are handled by the {@code GlobalExceptionHandler} and
 * returned as {@code ApiErrorResponse}.
 */
@RestController
@RequestMapping("/v1/applications/{appId}/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * Generate a new API key for the given application.
     * The full key is included in the response and is shown only once.
     *
     * @param appId   the application UUID
     * @param request optional key metadata (e.g. name)
     * @return 201 Created with the full API key (shown once)
     */
    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> generateApiKey(
            @PathVariable("appId") UUID appId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyCreatedResponse response = apiKeyService.generateApiKey(appId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all API keys for the given application.
     * Returns key metadata and a masked prefix only; full keys are never returned.
     *
     * @param appId the application UUID
     * @return 200 OK with the list of API keys
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(@PathVariable("appId") UUID appId) {
        List<ApiKeyResponse> responses = apiKeyService.listApiKeys(appId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Revoke (delete) an API key.
     *
     * @param appId the application UUID
     * @param keyId the API key UUID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeApiKey(
            @PathVariable("appId") UUID appId,
            @PathVariable("keyId") UUID keyId) {
        apiKeyService.revokeApiKey(appId, keyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revoke an API key (POST variant for portal compatibility).
     *
     * @param appId the application UUID
     * @param keyId the API key UUID
     * @return 204 No Content on success
     */
    @PostMapping("/{keyId}/revoke")
    public ResponseEntity<Void> revokeApiKeyPost(
            @PathVariable("appId") UUID appId,
            @PathVariable("keyId") UUID keyId) {
        apiKeyService.revokeApiKey(appId, keyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rotate an API key: revokes the current key and issues a new one.
     * The new full key is included in the response and is shown only once.
     *
     * @param appId the application UUID
     * @param keyId the API key UUID to rotate
     * @return 200 OK with the new key details
     */
    @PostMapping("/{keyId}/rotate")
    public ResponseEntity<RotateApiKeyResponse> rotateApiKey(
            @PathVariable("appId") UUID appId,
            @PathVariable("keyId") UUID keyId) {
        RotateApiKeyResponse response = apiKeyService.rotateApiKey(appId, keyId);
        return ResponseEntity.ok(response);
    }
}
