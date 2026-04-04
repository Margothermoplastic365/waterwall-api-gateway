package com.gateway.management.service;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.management.dto.ApprovalResponse;
import com.gateway.management.entity.ApprovalRequestEntity;
import com.gateway.management.repository.ApprovalRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @InjectMocks
    private ApprovalService approvalService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                userId.toString(), null, "admin@example.com",
                List.of("ADMIN"), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRequestApproval() {
        UUID resourceId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(approvalRequestRepository.save(any(ApprovalRequestEntity.class)))
                .thenAnswer(inv -> {
                    ApprovalRequestEntity e = inv.getArgument(0);
                    e.setId(approvalId);
                    return e;
                });

        ApprovalResponse response = approvalService.requestApproval("API_PUBLISH", resourceId);

        assertThat(response.getId()).isEqualTo(approvalId);
        assertThat(response.getType()).isEqualTo("API_PUBLISH");
        assertThat(response.getResourceId()).isEqualTo(resourceId);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getRequestedBy()).isEqualTo(userId);
        assertThat(response.getRequestedAt()).isNotNull();
        verify(approvalRequestRepository).save(any(ApprovalRequestEntity.class));
    }

    @Test
    void shouldApprove() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(approvalId)
                .type("API_PUBLISH")
                .resourceId(UUID.randomUUID())
                .status("PENDING")
                .requestedBy(userId)
                .requestedAt(Instant.now())
                .build();

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(entity));
        when(approvalRequestRepository.save(any(ApprovalRequestEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApprovalResponse response = approvalService.approve(approvalId, approverId);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getApprovedBy()).isEqualTo(approverId);
        assertThat(response.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldReject() {
        UUID approvalId = UUID.randomUUID();
        UUID rejecterId = UUID.randomUUID();

        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(approvalId)
                .type("API_PUBLISH")
                .resourceId(UUID.randomUUID())
                .status("PENDING")
                .requestedBy(userId)
                .requestedAt(Instant.now())
                .build();

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(entity));
        when(approvalRequestRepository.save(any(ApprovalRequestEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApprovalResponse response = approvalService.reject(approvalId, rejecterId, "Insufficient docs");

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getApprovedBy()).isEqualTo(rejecterId);
        assertThat(response.getRejectedReason()).isEqualTo("Insufficient docs");
        assertThat(response.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldListPending() {
        ApprovalRequestEntity pending = ApprovalRequestEntity.builder()
                .id(UUID.randomUUID())
                .type("SUBSCRIPTION")
                .resourceId(UUID.randomUUID())
                .status("PENDING")
                .requestedAt(Instant.now())
                .build();

        when(approvalRequestRepository.findByStatus("PENDING")).thenReturn(List.of(pending));

        List<ApprovalResponse> result = approvalService.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldThrowOnAlreadyResolved() {
        UUID approvalId = UUID.randomUUID();

        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(approvalId)
                .type("API_PUBLISH")
                .resourceId(UUID.randomUUID())
                .status("APPROVED")
                .requestedBy(userId)
                .requestedAt(Instant.now())
                .resolvedAt(Instant.now())
                .build();

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> approvalService.approve(approvalId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }
}
