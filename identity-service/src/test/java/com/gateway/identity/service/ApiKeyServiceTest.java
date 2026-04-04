package com.gateway.identity.service;

import com.gateway.common.cache.CacheInvalidationPublisher;
import com.gateway.identity.dto.ApiKeyCreatedResponse;
import com.gateway.identity.dto.ApiKeyResponse;
import com.gateway.identity.dto.CreateApiKeyRequest;
import com.gateway.identity.dto.RotateApiKeyResponse;
import com.gateway.identity.entity.ApiKeyEntity;
import com.gateway.identity.entity.ApplicationEntity;
import com.gateway.identity.entity.RevocationListEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.ApiKeyStatus;
import com.gateway.identity.repository.ApiKeyRepository;
import com.gateway.identity.repository.ApplicationRepository;
import com.gateway.identity.repository.RevocationListRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private RevocationListRepository revocationListRepository;
    @Mock private CacheInvalidationPublisher cacheInvalidationPublisher;
    @Mock private AuditService auditService;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private UUID appId;
    private ApplicationEntity application;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();

        user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .build();

        application = ApplicationEntity.builder()
                .id(appId)
                .name("My App")
                .user(user)
                .status("ACTIVE")
                .build();

        ReflectionTestUtils.setField(apiKeyService, "envPrefix", "live");
        ReflectionTestUtils.setField(apiKeyService, "rotationGraceHours", 168L);
    }

    // ── generateApiKey tests ────────────────────────────────────────────

    @Test
    void generateApiKey_success_returnsFullKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("prod-key");
        request.setEnvironmentSlug("prod");

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(inv -> {
            ApiKeyEntity k = inv.getArgument(0);
            k.setId(UUID.randomUUID());
            k.setCreatedAt(Instant.now());
            return k;
        });

        ApiKeyCreatedResponse result = apiKeyService.generateApiKey(appId, request);

        assertThat(result).isNotNull();
        assertThat(result.getFullKey()).isNotNull().startsWith("live_gw_live_");
        assertThat(result.getKeyPrefix()).isNotNull();
        assertThat(result.getName()).isEqualTo("prod-key");

        ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKeyEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(saved.getKeyHash()).isNotEmpty();
        assertThat(saved.getApplication()).isEqualTo(application);

        verify(auditService).logEvent(eq("apikey.created"), eq("API_KEY"), anyString(), eq("SUCCESS"));
    }

    @Test
    void generateApiKey_defaultEnvSlug_usesDev() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("test-key");
        // environmentSlug defaults to "dev"

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(inv -> {
            ApiKeyEntity k = inv.getArgument(0);
            k.setId(UUID.randomUUID());
            k.setCreatedAt(Instant.now());
            return k;
        });

        ApiKeyCreatedResponse result = apiKeyService.generateApiKey(appId, request);

        assertThat(result.getFullKey()).startsWith("dev_gw_live_");
    }

    @Test
    void generateApiKey_appNotFound_throwsEntityNotFound() {
        UUID unknownAppId = UUID.randomUUID();
        when(applicationRepository.findById(unknownAppId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.generateApiKey(unknownAppId, new CreateApiKeyRequest()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Application not found");
    }

    // ── listApiKeys tests ───────────────────────────────────────────────

    @Test
    void listApiKeys_returnsKeyResponses() {
        ApiKeyEntity key1 = ApiKeyEntity.builder()
                .id(UUID.randomUUID())
                .application(application)
                .name("key-1")
                .keyPrefix("dev_gw_live_abc")
                .keyHash("hash1")
                .environmentSlug("dev")
                .status(ApiKeyStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        ApiKeyEntity key2 = ApiKeyEntity.builder()
                .id(UUID.randomUUID())
                .application(application)
                .name("key-2")
                .keyPrefix("dev_gw_live_def")
                .keyHash("hash2")
                .environmentSlug("dev")
                .status(ApiKeyStatus.REVOKED)
                .createdAt(Instant.now())
                .build();

        when(apiKeyRepository.findByApplicationId(appId)).thenReturn(List.of(key1, key2));

        List<ApiKeyResponse> result = apiKeyService.listApiKeys(appId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("key-1");
        assertThat(result.get(0).getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(result.get(1).getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
    }

    @Test
    void listApiKeys_noKeys_returnsEmptyList() {
        when(apiKeyRepository.findByApplicationId(appId)).thenReturn(List.of());

        List<ApiKeyResponse> result = apiKeyService.listApiKeys(appId);

        assertThat(result).isEmpty();
    }

    // ── revokeApiKey tests ──────────────────────────────────────────────

    @Test
    void revokeApiKey_success_setsRevokedAndAddsToRevocationList() {
        UUID keyId = UUID.randomUUID();
        ApiKeyEntity key = ApiKeyEntity.builder()
                .id(keyId)
                .application(application)
                .keyPrefix("dev_gw_live_abc")
                .keyHash("hash123")
                .status(ApiKeyStatus.ACTIVE)
                .build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenReturn(key);

        apiKeyService.revokeApiKey(appId, keyId);

        ArgumentCaptor<ApiKeyEntity> keyCaptor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(apiKeyRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getStatus()).isEqualTo(ApiKeyStatus.REVOKED);

        ArgumentCaptor<RevocationListEntity> revCaptor = ArgumentCaptor.forClass(RevocationListEntity.class);
        verify(revocationListRepository).save(revCaptor.capture());
        assertThat(revCaptor.getValue().getRevocationType()).isEqualTo("API_KEY");
        assertThat(revCaptor.getValue().getCredentialId()).isEqualTo("dev_gw_live_abc");

        verify(cacheInvalidationPublisher).invalidate(anyString(), eq("hash123"), anyString());
        verify(auditService).logEvent(eq("apikey.revoked"), eq("API_KEY"), eq(keyId.toString()), eq("SUCCESS"));
    }

    @Test
    void revokeApiKey_keyNotFound_throwsEntityNotFound() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(appId, keyId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("API key not found");
    }

    @Test
    void revokeApiKey_keyBelongsToDifferentApp_throwsIllegalArgument() {
        UUID keyId = UUID.randomUUID();
        UUID otherAppId = UUID.randomUUID();
        ApplicationEntity otherApp = ApplicationEntity.builder()
                .id(otherAppId)
                .name("Other App")
                .build();

        ApiKeyEntity key = ApiKeyEntity.builder()
                .id(keyId)
                .application(otherApp)
                .keyPrefix("dev_gw_live_xyz")
                .keyHash("hash456")
                .status(ApiKeyStatus.ACTIVE)
                .build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(appId, keyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to application");
    }

    // ── rotateApiKey tests ──────────────────────────────────────────────

    @Test
    void rotateApiKey_success_createsNewKeyAndRetiresOld() {
        UUID oldKeyId = UUID.randomUUID();
        ApiKeyEntity oldKey = ApiKeyEntity.builder()
                .id(oldKeyId)
                .application(application)
                .name("old-key")
                .keyPrefix("dev_gw_live_old")
                .keyHash("oldHash")
                .status(ApiKeyStatus.ACTIVE)
                .build();

        when(apiKeyRepository.findById(oldKeyId)).thenReturn(Optional.of(oldKey));
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(inv -> {
            ApiKeyEntity k = inv.getArgument(0);
            if (k.getId() == null) {
                k.setId(UUID.randomUUID());
            }
            return k;
        });

        RotateApiKeyResponse result = apiKeyService.rotateApiKey(appId, oldKeyId);

        assertThat(result.getNewKeyId()).isNotNull();
        assertThat(result.getNewFullKey()).isNotNull();
        assertThat(result.getOldKeyId()).isEqualTo(oldKeyId);
        assertThat(result.getOldKeyExpiresAt()).isNotNull();

        // Verify two saves: one for new key, one for old key update
        verify(apiKeyRepository, times(2)).save(any(ApiKeyEntity.class));

        // Old key should be ROTATED with expiry
        assertThat(oldKey.getStatus()).isEqualTo(ApiKeyStatus.ROTATED);
        assertThat(oldKey.getExpiresAt()).isNotNull();

        verify(cacheInvalidationPublisher).invalidate(anyString(), eq("oldHash"), anyString());
        verify(auditService).logEvent(eq("apikey.rotated"), eq("API_KEY"), eq(oldKeyId.toString()), eq("SUCCESS"));
    }
}
