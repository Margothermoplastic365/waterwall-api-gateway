package com.gateway.management.service;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.events.EventPublisher;
import com.gateway.management.dto.ApiResponse;
import com.gateway.management.dto.CreateApiRequest;
import com.gateway.management.dto.UpdateApiRequest;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.entity.enums.ApiStatus;
import com.gateway.management.entity.enums.Visibility;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiServiceTest {

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ApiService apiService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                userId.toString(), orgId.toString(), "test@example.com",
                List.of("ADMIN"), List.of("api:manage"), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateApi() {
        CreateApiRequest request = CreateApiRequest.builder()
                .name("Pet Store")
                .version("1.0.0")
                .description("A pet store API")
                .visibility(Visibility.PUBLIC)
                .protocolType("REST")
                .backendBaseUrl("https://petstore.example.com")
                .tags(List.of("pets", "store"))
                .category("commerce")
                .build();

        UUID apiId = UUID.randomUUID();
        ApiEntity savedEntity = ApiEntity.builder()
                .id(apiId)
                .name("Pet Store")
                .version("1.0.0")
                .description("A pet store API")
                .status(ApiStatus.CREATED)
                .visibility(Visibility.PUBLIC)
                .protocolType("REST")
                .backendBaseUrl("https://petstore.example.com")
                .tags(List.of("pets", "store"))
                .category("commerce")
                .createdBy(userId)
                .orgId(orgId)
                .build();

        // First save returns entity, second save (setting apiGroupId) also returns it
        when(apiRepository.save(any(ApiEntity.class))).thenReturn(savedEntity);

        ApiResponse response = apiService.createApi(request);

        assertThat(response.getName()).isEqualTo("Pet Store");
        assertThat(response.getStatus()).isEqualTo(ApiStatus.CREATED);
        assertThat(response.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.getBackendBaseUrl()).isEqualTo("https://petstore.example.com");
        assertThat(response.getRoutes()).isEmpty();

        // save is called twice: once for initial creation, once for setting apiGroupId
        verify(apiRepository, times(2)).save(any(ApiEntity.class));
    }

    @Test
    void shouldGetApiById() {
        UUID apiId = UUID.randomUUID();
        ApiEntity entity = ApiEntity.builder()
                .id(apiId)
                .name("Test API")
                .version("2.0")
                .status(ApiStatus.PUBLISHED)
                .build();

        RouteEntity route = RouteEntity.builder()
                .id(UUID.randomUUID())
                .api(entity)
                .path("/pets")
                .method("GET")
                .upstreamUrl("http://backend/pets")
                .enabled(true)
                .priority(0)
                .stripPrefix(false)
                .build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(entity));
        when(routeRepository.findByApiId(apiId)).thenReturn(List.of(route));

        ApiResponse response = apiService.getApi(apiId);

        assertThat(response.getId()).isEqualTo(apiId);
        assertThat(response.getName()).isEqualTo("Test API");
        assertThat(response.getRoutes()).hasSize(1);
        assertThat(response.getRoutes().get(0).getPath()).isEqualTo("/pets");
    }

    @Test
    void shouldThrowWhenApiNotFound() {
        UUID apiId = UUID.randomUUID();
        when(apiRepository.findById(apiId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiService.getApi(apiId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(apiId.toString());
    }

    @Test
    void shouldUpdateApi() {
        UUID apiId = UUID.randomUUID();
        ApiEntity existing = ApiEntity.builder()
                .id(apiId)
                .name("Old Name")
                .version("1.0")
                .description("Old description")
                .status(ApiStatus.CREATED)
                .category("old-category")
                .build();

        UpdateApiRequest request = new UpdateApiRequest();
        request.setName("New Name");
        request.setDescription("New description");
        // version, category etc. are null -- should NOT be overwritten

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(existing));
        when(apiRepository.save(any(ApiEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(routeRepository.findByApiId(apiId)).thenReturn(Collections.emptyList());

        ApiResponse response = apiService.updateApi(apiId, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getDescription()).isEqualTo("New description");
        // version should remain unchanged (null field in request = no update)
        assertThat(response.getVersion()).isEqualTo("1.0");
        assertThat(response.getCategory()).isEqualTo("old-category");
    }

    @Test
    void shouldPublishApi() {
        UUID apiId = UUID.randomUUID();
        ApiEntity entity = ApiEntity.builder()
                .id(apiId)
                .name("Publishable API")
                .status(ApiStatus.CREATED)
                .build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(entity));
        when(apiRepository.save(any(ApiEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(routeRepository.findByApiId(apiId)).thenReturn(Collections.emptyList());

        ApiResponse response = apiService.publishApi(apiId);

        assertThat(response.getStatus()).isEqualTo(ApiStatus.PUBLISHED);
        verify(eventPublisher).publish(anyString(), eq("api.published"), any());
    }

    @Test
    void shouldRetireApi() {
        UUID apiId = UUID.randomUUID();
        ApiEntity entity = ApiEntity.builder()
                .id(apiId)
                .name("Retiring API")
                .status(ApiStatus.PUBLISHED)
                .build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(entity));
        when(apiRepository.save(any(ApiEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(routeRepository.findByApiId(apiId)).thenReturn(Collections.emptyList());

        ApiResponse response = apiService.retireApi(apiId);

        assertThat(response.getStatus()).isEqualTo(ApiStatus.RETIRED);
        verify(eventPublisher).publish(anyString(), eq("api.retired"), any());
    }

    @Test
    void shouldDeleteApi() {
        UUID apiId = UUID.randomUUID();
        ApiEntity entity = ApiEntity.builder()
                .id(apiId)
                .name("To Delete")
                .status(ApiStatus.CREATED)
                .build();

        when(apiRepository.findById(apiId)).thenReturn(Optional.of(entity));
        when(apiRepository.save(any(ApiEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        apiService.deleteApi(apiId);

        ArgumentCaptor<ApiEntity> captor = ArgumentCaptor.forClass(ApiEntity.class);
        verify(apiRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApiStatus.RETIRED);
    }
}
