package com.gateway.identity.controller;

import com.gateway.identity.dto.*;
import com.gateway.identity.entity.enums.ApiKeyStatus;
import com.gateway.identity.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private ApiKeyController apiKeyController;

    private static final UUID APP_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    // ── generateApiKey ──────────────────────────────────────────────────

    @Test
    void generateApiKey_returnsCreatedWithFullKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("my-key");

        ApiKeyCreatedResponse expected = ApiKeyCreatedResponse.builder()
                .id(KEY_ID)
                .name("my-key")
                .keyPrefix("gw_abc")
                .fullKey("gw_abc_full_secret")
                .createdAt(Instant.now())
                .build();

        when(apiKeyService.generateApiKey(APP_ID, request)).thenReturn(expected);

        ResponseEntity<ApiKeyCreatedResponse> response =
                apiKeyController.generateApiKey(APP_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(apiKeyService).generateApiKey(APP_ID, request);
        verifyNoMoreInteractions(apiKeyService);
    }

    // ── listApiKeys ─────────────────────────────────────────────────────

    @Test
    void listApiKeys_returnsOkWithList() {
        ApiKeyResponse key = ApiKeyResponse.builder()
                .id(KEY_ID)
                .name("my-key")
                .keyPrefix("gw_abc")
                .environmentSlug("dev")
                .status(ApiKeyStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(apiKeyService.listApiKeys(APP_ID)).thenReturn(List.of(key));

        ResponseEntity<List<ApiKeyResponse>> response =
                apiKeyController.listApiKeys(APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("my-key");
        verify(apiKeyService).listApiKeys(APP_ID);
    }

    // ── revokeApiKey (DELETE) ───────────────────────────────────────────

    @Test
    void revokeApiKey_returnsNoContent() {
        ResponseEntity<Void> response = apiKeyController.revokeApiKey(APP_ID, KEY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(apiKeyService).revokeApiKey(APP_ID, KEY_ID);
    }

    // ── revokeApiKeyPost (POST variant) ─────────────────────────────────

    @Test
    void revokeApiKeyPost_returnsNoContent() {
        ResponseEntity<Void> response = apiKeyController.revokeApiKeyPost(APP_ID, KEY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(apiKeyService).revokeApiKey(APP_ID, KEY_ID);
    }

    // ── rotateApiKey ────────────────────────────────────────────────────

    @Test
    void rotateApiKey_returnsOkWithNewKey() {
        UUID newKeyId = UUID.randomUUID();
        RotateApiKeyResponse expected = RotateApiKeyResponse.builder()
                .newKeyId(newKeyId)
                .newKeyPrefix("gw_xyz")
                .newFullKey("gw_xyz_full_secret")
                .oldKeyId(KEY_ID)
                .oldKeyPrefix("gw_abc")
                .build();

        when(apiKeyService.rotateApiKey(APP_ID, KEY_ID)).thenReturn(expected);

        ResponseEntity<RotateApiKeyResponse> response =
                apiKeyController.rotateApiKey(APP_ID, KEY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(apiKeyService).rotateApiKey(APP_ID, KEY_ID);
    }
}
