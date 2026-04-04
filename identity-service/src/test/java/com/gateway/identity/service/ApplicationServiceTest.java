package com.gateway.identity.service;

import com.gateway.identity.dto.ApplicationResponse;
import com.gateway.identity.dto.CreateApplicationRequest;
import com.gateway.identity.dto.UpdateApplicationRequest;
import com.gateway.identity.entity.ApiKeyEntity;
import com.gateway.identity.entity.ApplicationEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.ApiKeyStatus;
import com.gateway.identity.repository.ApiKeyRepository;
import com.gateway.identity.repository.ApplicationRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private ApplicationService applicationService;

    private UUID userId;
    private UUID appId;
    private UserEntity user;
    private ApplicationEntity application;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appId = UUID.randomUUID();

        user = UserEntity.builder()
                .id(userId)
                .email("dev@example.com")
                .build();

        application = ApplicationEntity.builder()
                .id(appId)
                .name("My App")
                .description("A test app")
                .user(user)
                .status("ACTIVE")
                .callbackUrls(List.of("https://example.com/callback"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── createApplication tests ─────────────────────────────────────────

    @Test
    void createApplication_success_savesAndReturnsResponse() {
        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .name("New App")
                .description("Description")
                .callbackUrls(List.of("https://example.com"))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(applicationRepository.save(any(ApplicationEntity.class))).thenAnswer(inv -> {
            ApplicationEntity app = inv.getArgument(0);
            app.setId(UUID.randomUUID());
            app.setCreatedAt(Instant.now());
            app.setUpdatedAt(Instant.now());
            return app;
        });

        ApplicationResponse result = applicationService.createApplication(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New App");
        assertThat(result.getDescription()).isEqualTo("Description");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCallbackUrls()).containsExactly("https://example.com");

        verify(auditService).logEvent(eq("application.created"), eq("APPLICATION"), anyString(), eq("SUCCESS"));
    }

    @Test
    void createApplication_userNotFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .name("New App")
                .build();

        assertThatThrownBy(() -> applicationService.createApplication(unknownId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── listMyApplications tests ────────────────────────────────────────

    @Test
    void listMyApplications_returnsNonDeletedApps() {
        when(applicationRepository.findByUserIdAndStatusNot(userId, "DELETED"))
                .thenReturn(List.of(application));

        List<ApplicationResponse> result = applicationService.listMyApplications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("My App");
    }

    @Test
    void listMyApplications_noApps_returnsEmptyList() {
        when(applicationRepository.findByUserIdAndStatusNot(userId, "DELETED"))
                .thenReturn(List.of());

        List<ApplicationResponse> result = applicationService.listMyApplications(userId);

        assertThat(result).isEmpty();
    }

    // ── getApplication tests ────────────────────────────────────────────

    @Test
    void getApplication_existingApp_returnsResponse() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        ApplicationResponse result = applicationService.getApplication(appId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(appId);
        assertThat(result.getName()).isEqualTo("My App");
    }

    @Test
    void getApplication_notFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(applicationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(unknownId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Application not found");
    }

    // ── updateApplication tests ─────────────────────────────────────────

    @Test
    void updateApplication_partialUpdate_appliesOnlyNonNullFields() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(ApplicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateApplicationRequest request = new UpdateApplicationRequest();
        request.setName("Updated Name");

        ApplicationResponse result = applicationService.updateApplication(appId, userId, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        // Description should remain unchanged
        assertThat(result.getDescription()).isEqualTo("A test app");
    }

    @Test
    void updateApplication_notOwner_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        UpdateApplicationRequest request = new UpdateApplicationRequest();
        request.setName("Hacked Name");

        assertThatThrownBy(() -> applicationService.updateApplication(appId, otherUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not own application");
    }

    @Test
    void updateApplication_notFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(applicationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.updateApplication(unknownId, userId, new UpdateApplicationRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── deleteApplication tests ─────────────────────────────────────────

    @Test
    void deleteApplication_success_softDeletesAndRevokesKeys() {
        ApiKeyEntity activeKey = ApiKeyEntity.builder()
                .id(UUID.randomUUID())
                .application(application)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        ApiKeyEntity rotatedKey = ApiKeyEntity.builder()
                .id(UUID.randomUUID())
                .application(application)
                .status(ApiKeyStatus.ROTATED)
                .build();
        ApiKeyEntity alreadyRevoked = ApiKeyEntity.builder()
                .id(UUID.randomUUID())
                .application(application)
                .status(ApiKeyStatus.REVOKED)
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(apiKeyRepository.findByApplicationId(appId))
                .thenReturn(List.of(activeKey, rotatedKey, alreadyRevoked));
        when(applicationRepository.save(any(ApplicationEntity.class))).thenReturn(application);

        applicationService.deleteApplication(appId, userId);

        // Only active and rotated keys should be revoked
        verify(apiKeyRepository, times(2)).save(any(ApiKeyEntity.class));
        assertThat(activeKey.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(rotatedKey.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);

        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DELETED");

        verify(auditService).logEvent(eq("application.deleted"), eq("APPLICATION"), eq(appId.toString()), eq("SUCCESS"));
    }

    @Test
    void deleteApplication_notOwner_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.deleteApplication(appId, otherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not own application");
    }

    @Test
    void deleteApplication_appNotFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(applicationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.deleteApplication(unknownId, userId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
