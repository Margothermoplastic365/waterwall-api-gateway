package com.gateway.management.controller;

import com.gateway.management.dto.AttachPolicyRequest;
import com.gateway.management.dto.CreatePolicyRequest;
import com.gateway.management.dto.PolicyAttachmentResponse;
import com.gateway.management.dto.PolicyResponse;
import com.gateway.management.service.PolicyService;
import com.gateway.management.service.TransformTemplateProvider;
import com.gateway.management.service.TransformTemplateProvider.TransformTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private TransformTemplateProvider transformTemplateProvider;

    @InjectMocks
    private PolicyController policyController;

    @Test
    void createPolicy_returnsCreated() {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .name("Rate Limit")
                .type("RATE_LIMIT")
                .config(Map.of("requestsPerSecond", 10))
                .build();
        PolicyResponse expected = PolicyResponse.builder()
                .id(UUID.randomUUID())
                .name("Rate Limit")
                .type("RATE_LIMIT")
                .build();
        when(policyService.createPolicy(request)).thenReturn(expected);

        ResponseEntity<PolicyResponse> response = policyController.createPolicy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(policyService).createPolicy(request);
    }

    @Test
    void listPolicies_returnsOk() {
        List<PolicyResponse> policies = List.of(
                PolicyResponse.builder().id(UUID.randomUUID()).name("Policy 1").build()
        );
        when(policyService.listPolicies()).thenReturn(policies);

        ResponseEntity<List<PolicyResponse>> response = policyController.listPolicies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(policyService).listPolicies();
    }

    @Test
    void getPolicy_returnsOk() {
        UUID id = UUID.randomUUID();
        PolicyResponse expected = PolicyResponse.builder().id(id).name("Auth Policy").build();
        when(policyService.getPolicy(id)).thenReturn(expected);

        ResponseEntity<PolicyResponse> response = policyController.getPolicy(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(policyService).getPolicy(id);
    }

    @Test
    void updatePolicy_returnsOk() {
        UUID id = UUID.randomUUID();
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .name("Updated")
                .type("CACHE")
                .config(Map.of("ttl", 60))
                .build();
        PolicyResponse expected = PolicyResponse.builder().id(id).name("Updated").build();
        when(policyService.updatePolicy(id, request)).thenReturn(expected);

        ResponseEntity<PolicyResponse> response = policyController.updatePolicy(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(policyService).updatePolicy(id, request);
    }

    @Test
    void deletePolicy_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = policyController.deletePolicy(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(policyService).deletePolicy(id);
    }

    @Test
    void attachPolicy_returnsCreated() {
        AttachPolicyRequest request = AttachPolicyRequest.builder()
                .policyId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .scope("API")
                .priority(1)
                .build();

        ResponseEntity<Void> response = policyController.attachPolicy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(policyService).attachPolicy(request);
    }

    @Test
    void attachPolicyAlt_returnsCreated() {
        AttachPolicyRequest request = AttachPolicyRequest.builder()
                .policyId(UUID.randomUUID())
                .apiId(UUID.randomUUID())
                .build();

        ResponseEntity<Void> response = policyController.attachPolicyAlt(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(policyService).attachPolicy(request);
    }

    @Test
    void detachPolicy_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = policyController.detachPolicy(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(policyService).detachPolicy(id);
    }

    @Test
    void listAttachments_returnsOk() {
        List<PolicyAttachmentResponse> attachments = List.of(
                PolicyAttachmentResponse.builder().id(UUID.randomUUID()).policyName("P1").build()
        );
        when(policyService.listAllAttachments()).thenReturn(attachments);

        ResponseEntity<List<PolicyAttachmentResponse>> response = policyController.listAttachments();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(policyService).listAllAttachments();
    }

    @Test
    void getAttachmentsForApi_returnsOk() {
        UUID apiId = UUID.randomUUID();
        List<PolicyAttachmentResponse> attachments = List.of(
                PolicyAttachmentResponse.builder().id(UUID.randomUUID()).apiId(apiId).build()
        );
        when(policyService.getAttachmentsForApi(apiId)).thenReturn(attachments);

        ResponseEntity<List<PolicyAttachmentResponse>> response = policyController.getAttachmentsForApi(apiId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(policyService).getAttachmentsForApi(apiId);
    }

    @Test
    void getPoliciesForApi_returnsOk() {
        UUID apiId = UUID.randomUUID();
        List<PolicyResponse> policies = List.of(
                PolicyResponse.builder().id(UUID.randomUUID()).name("P1").build()
        );
        when(policyService.getPoliciesForApi(apiId)).thenReturn(policies);

        ResponseEntity<List<PolicyResponse>> response = policyController.getPoliciesForApi(apiId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(policyService).getPoliciesForApi(apiId);
    }

    @Test
    void getTransformTemplates_returnsOk() {
        List<TransformTemplate> templates = List.of(
                new TransformTemplate("Template 1", "Description", "request", Map.of())
        );
        when(transformTemplateProvider.getTemplates()).thenReturn(templates);

        ResponseEntity<List<TransformTemplate>> response = policyController.getTransformTemplates();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Template 1");
        verify(transformTemplateProvider).getTemplates();
    }
}
