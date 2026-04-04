package com.gateway.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.management.dto.AttachPolicyRequest;
import com.gateway.management.dto.CreatePolicyRequest;
import com.gateway.management.dto.PolicyResponse;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.PolicyAttachmentEntity;
import com.gateway.management.entity.PolicyEntity;
import com.gateway.management.entity.RouteEntity;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.PolicyAttachmentRepository;
import com.gateway.management.repository.PolicyRepository;
import com.gateway.management.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyAttachmentRepository policyAttachmentRepository;

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PolicyService policyService;

    @Test
    void shouldCreatePolicy() throws Exception {
        Map<String, Object> config = Map.of("requestsPerSecond", 100);
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .name("Rate Limit Policy")
                .type("RATE_LIMIT")
                .config(config)
                .description("Limits to 100 rps")
                .build();

        when(objectMapper.writeValueAsString(config)).thenReturn("{\"requestsPerSecond\":100}");

        UUID policyId = UUID.randomUUID();
        PolicyEntity savedEntity = PolicyEntity.builder()
                .id(policyId)
                .name("Rate Limit Policy")
                .type("RATE_LIMIT")
                .config("{\"requestsPerSecond\":100}")
                .description("Limits to 100 rps")
                .version(1)
                .build();

        when(policyRepository.save(any(PolicyEntity.class))).thenReturn(savedEntity);

        PolicyResponse response = policyService.createPolicy(request);

        assertThat(response.getId()).isEqualTo(policyId);
        assertThat(response.getName()).isEqualTo("Rate Limit Policy");
        assertThat(response.getType()).isEqualTo("RATE_LIMIT");
        assertThat(response.getConfig()).isEqualTo("{\"requestsPerSecond\":100}");
        verify(policyRepository).save(any(PolicyEntity.class));
    }

    @Test
    void shouldListPolicies() {
        PolicyEntity p1 = PolicyEntity.builder().id(UUID.randomUUID()).name("P1").type("RATE_LIMIT").config("{}").version(1).build();
        PolicyEntity p2 = PolicyEntity.builder().id(UUID.randomUUID()).name("P2").type("AUTH").config("{}").version(1).build();

        when(policyRepository.findAll()).thenReturn(List.of(p1, p2));

        List<PolicyResponse> result = policyService.listPolicies();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PolicyResponse::getName).containsExactly("P1", "P2");
    }

    @Test
    void shouldGetPolicy() {
        UUID id = UUID.randomUUID();
        PolicyEntity entity = PolicyEntity.builder()
                .id(id).name("My Policy").type("CORS").config("{}").version(1).build();

        when(policyRepository.findById(id)).thenReturn(Optional.of(entity));

        PolicyResponse response = policyService.getPolicy(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("My Policy");
    }

    @Test
    void shouldThrowWhenPolicyNotFound() {
        UUID id = UUID.randomUUID();
        when(policyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.getPolicy(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void shouldUpdatePolicy() throws Exception {
        UUID id = UUID.randomUUID();
        PolicyEntity existing = PolicyEntity.builder()
                .id(id).name("Old Name").type("RATE_LIMIT").config("{}").version(1).build();

        Map<String, Object> newConfig = Map.of("requestsPerSecond", 200);
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .name("Updated Name")
                .config(newConfig)
                .build();

        when(policyRepository.findById(id)).thenReturn(Optional.of(existing));
        when(objectMapper.writeValueAsString(newConfig)).thenReturn("{\"requestsPerSecond\":200}");
        when(policyRepository.save(any(PolicyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyResponse response = policyService.updatePolicy(id, request);

        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getType()).isEqualTo("RATE_LIMIT"); // type not updated (null in request)
        assertThat(response.getVersion()).isEqualTo(2); // incremented from 1
    }

    @Test
    void shouldDeletePolicy() {
        UUID id = UUID.randomUUID();
        PolicyEntity entity = PolicyEntity.builder()
                .id(id).name("To Delete").type("AUTH").config("{}").version(1).build();

        PolicyAttachmentEntity attachment = PolicyAttachmentEntity.builder()
                .id(UUID.randomUUID()).policy(entity).scope("API").build();

        when(policyRepository.findById(id)).thenReturn(Optional.of(entity));
        when(policyAttachmentRepository.findByPolicy_Id(id)).thenReturn(List.of(attachment));

        policyService.deletePolicy(id);

        verify(policyAttachmentRepository).deleteAll(List.of(attachment));
        verify(policyRepository).delete(entity);
    }

    @Test
    void shouldAttachPolicy() {
        UUID policyId = UUID.randomUUID();
        UUID apiId = UUID.randomUUID();

        PolicyEntity policy = PolicyEntity.builder()
                .id(policyId).name("Policy").type("RATE_LIMIT").config("{}").version(1).build();
        ApiEntity api = ApiEntity.builder().id(apiId).name("API").build();

        AttachPolicyRequest request = AttachPolicyRequest.builder()
                .policyId(policyId)
                .apiId(apiId)
                .scope("API")
                .priority(1)
                .build();

        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(apiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(policyAttachmentRepository.save(any(PolicyAttachmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        policyService.attachPolicy(request);

        ArgumentCaptor<PolicyAttachmentEntity> captor = ArgumentCaptor.forClass(PolicyAttachmentEntity.class);
        verify(policyAttachmentRepository).save(captor.capture());

        PolicyAttachmentEntity saved = captor.getValue();
        assertThat(saved.getPolicy().getId()).isEqualTo(policyId);
        assertThat(saved.getApi().getId()).isEqualTo(apiId);
        assertThat(saved.getScope()).isEqualTo("API");
    }

    @Test
    void shouldDetachPolicy() {
        UUID attachmentId = UUID.randomUUID();
        PolicyEntity policy = PolicyEntity.builder()
                .id(UUID.randomUUID()).name("P").type("AUTH").config("{}").version(1).build();
        PolicyAttachmentEntity attachment = PolicyAttachmentEntity.builder()
                .id(attachmentId).policy(policy).scope("API").build();

        when(policyAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        policyService.detachPolicy(attachmentId);

        verify(policyAttachmentRepository).delete(attachment);
    }
}
