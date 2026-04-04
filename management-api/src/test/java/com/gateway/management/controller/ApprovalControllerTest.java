package com.gateway.management.controller;

import com.gateway.management.dto.ApprovalActionRequest;
import com.gateway.management.dto.ApprovalResponse;
import com.gateway.management.service.ApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.gateway.common.auth.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ApprovalController approvalController;

    @Test
    void requestApproval_returnsCreated() {
        UUID resourceId = UUID.randomUUID();
        ApprovalActionRequest request = new ApprovalActionRequest();
        request.setType("SUBSCRIPTION");
        request.setResourceId(resourceId);

        ApprovalResponse expected = ApprovalResponse.builder()
                .id(UUID.randomUUID())
                .type("SUBSCRIPTION")
                .resourceId(resourceId)
                .status("PENDING")
                .build();
        when(approvalService.requestApproval("SUBSCRIPTION", resourceId)).thenReturn(expected);

        ResponseEntity<ApprovalResponse> response = approvalController.requestApproval(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(approvalService).requestApproval("SUBSCRIPTION", resourceId);
    }

    @Test
    void approve_returnsOk() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        ApprovalResponse expected = ApprovalResponse.builder()
                .id(approvalId)
                .status("APPROVED")
                .approvedBy(approverId)
                .build();

        try (MockedStatic<SecurityContextHelper> mocked = mockStatic(SecurityContextHelper.class)) {
            mocked.when(SecurityContextHelper::getCurrentUserId).thenReturn(approverId.toString());
            when(approvalService.approve(approvalId, approverId)).thenReturn(expected);

            ResponseEntity<ApprovalResponse> response = approvalController.approve(approvalId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
            verify(approvalService).approve(approvalId, approverId);
        }
    }

    @Test
    void approve_withNoAuthenticatedUser_passesNullApproverId() {
        UUID approvalId = UUID.randomUUID();

        ApprovalResponse expected = ApprovalResponse.builder()
                .id(approvalId)
                .status("APPROVED")
                .build();

        try (MockedStatic<SecurityContextHelper> mocked = mockStatic(SecurityContextHelper.class)) {
            mocked.when(SecurityContextHelper::getCurrentUserId).thenReturn(null);
            when(approvalService.approve(approvalId, null)).thenReturn(expected);

            ResponseEntity<ApprovalResponse> response = approvalController.approve(approvalId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
            verify(approvalService).approve(approvalId, null);
        }
    }

    @Test
    void reject_returnsOk() {
        UUID approvalId = UUID.randomUUID();
        UUID rejecterId = UUID.randomUUID();

        ApprovalActionRequest request = new ApprovalActionRequest();
        request.setReason("Not compliant");

        ApprovalResponse expected = ApprovalResponse.builder()
                .id(approvalId)
                .status("REJECTED")
                .rejectedReason("Not compliant")
                .build();

        try (MockedStatic<SecurityContextHelper> mocked = mockStatic(SecurityContextHelper.class)) {
            mocked.when(SecurityContextHelper::getCurrentUserId).thenReturn(rejecterId.toString());
            when(approvalService.reject(approvalId, rejecterId, "Not compliant")).thenReturn(expected);

            ResponseEntity<ApprovalResponse> response = approvalController.reject(approvalId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
            verify(approvalService).reject(approvalId, rejecterId, "Not compliant");
        }
    }

    @Test
    void reject_withNoAuthenticatedUser_passesNullRejecterId() {
        UUID approvalId = UUID.randomUUID();

        ApprovalActionRequest request = new ApprovalActionRequest();
        request.setReason("Policy violation");

        ApprovalResponse expected = ApprovalResponse.builder()
                .id(approvalId)
                .status("REJECTED")
                .build();

        try (MockedStatic<SecurityContextHelper> mocked = mockStatic(SecurityContextHelper.class)) {
            mocked.when(SecurityContextHelper::getCurrentUserId).thenReturn(null);
            when(approvalService.reject(approvalId, null, "Policy violation")).thenReturn(expected);

            ResponseEntity<ApprovalResponse> response = approvalController.reject(approvalId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(approvalService).reject(approvalId, null, "Policy violation");
        }
    }

    @Test
    void listAll_returnsOk() {
        List<ApprovalResponse> approvals = List.of(
                ApprovalResponse.builder().id(UUID.randomUUID()).status("PENDING").build(),
                ApprovalResponse.builder().id(UUID.randomUUID()).status("APPROVED").build()
        );
        when(approvalService.listAll()).thenReturn(approvals);

        ResponseEntity<List<ApprovalResponse>> response = approvalController.listAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(approvalService).listAll();
    }

    @Test
    void listPending_returnsOk() {
        List<ApprovalResponse> pending = List.of(
                ApprovalResponse.builder().id(UUID.randomUUID()).status("PENDING").build()
        );
        when(approvalService.listPending()).thenReturn(pending);

        ResponseEntity<List<ApprovalResponse>> response = approvalController.listPending();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(approvalService).listPending();
    }
}
