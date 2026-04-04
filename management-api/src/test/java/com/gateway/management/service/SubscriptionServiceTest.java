package com.gateway.management.service;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.events.EventPublisher;
import com.gateway.management.dto.CreateSubscriptionRequest;
import com.gateway.management.dto.SubscriptionResponse;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.PlanEntity;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.PlanRepository;
import com.gateway.management.repository.SubscriptionRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                userId.toString(), null, "test@example.com",
                List.of("ADMIN"), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ApiEntity sampleApi() {
        return ApiEntity.builder().id(UUID.randomUUID()).name("Test API").build();
    }

    private PlanEntity samplePlan() {
        return PlanEntity.builder().id(UUID.randomUUID()).name("Basic Plan").build();
    }

    private SubscriptionEntity buildSubscription(SubStatus status) {
        ApiEntity api = sampleApi();
        PlanEntity plan = samplePlan();
        return SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .api(api)
                .plan(plan)
                .environmentSlug("dev")
                .status(status)
                .build();
    }

    @Test
    void shouldCreateSubscriptionPendingForProd() {
        ApiEntity api = sampleApi();
        PlanEntity plan = samplePlan();
        UUID appId = UUID.randomUUID();

        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setApplicationId(appId);
        request.setApiId(api.getId());
        request.setPlanId(plan.getId());
        request.setEnvironmentSlug("prod");

        when(apiRepository.findById(api.getId())).thenReturn(Optional.of(api));
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByApplicationIdAndApiId(appId, api.getId()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(inv -> {
                    SubscriptionEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        SubscriptionResponse response = subscriptionService.subscribe(request);

        assertThat(response.getStatus()).isEqualTo(SubStatus.PENDING);
        assertThat(response.getApprovedAt()).isNull();
        verify(eventPublisher).publish(anyString(), eq("subscription.created"), any());
    }

    @Test
    void shouldCreateSubscriptionApprovedForNonProd() {
        ApiEntity api = sampleApi();
        PlanEntity plan = samplePlan();
        UUID appId = UUID.randomUUID();

        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setApplicationId(appId);
        request.setApiId(api.getId());
        request.setPlanId(plan.getId());
        request.setEnvironmentSlug("dev");

        when(apiRepository.findById(api.getId())).thenReturn(Optional.of(api));
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByApplicationIdAndApiId(appId, api.getId()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(inv -> {
                    SubscriptionEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        SubscriptionResponse response = subscriptionService.subscribe(request);

        assertThat(response.getStatus()).isEqualTo(SubStatus.APPROVED);
        assertThat(response.getApprovedAt()).isNotNull();
    }

    @Test
    void shouldApproveSubscription() {
        SubscriptionEntity entity = buildSubscription(SubStatus.PENDING);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.approveSubscription(entity.getId(), "Looks good");

        assertThat(response.getStatus()).isEqualTo(SubStatus.APPROVED);
        assertThat(entity.getApprovedAt()).isNotNull();
        assertThat(entity.getReason()).isEqualTo("Looks good");
        verify(eventPublisher).publish(anyString(), eq("subscription.approved"), any());
    }

    @Test
    void shouldRejectSubscription() {
        SubscriptionEntity entity = buildSubscription(SubStatus.PENDING);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.rejectSubscription(entity.getId(), "Not compliant");

        assertThat(response.getStatus()).isEqualTo(SubStatus.REJECTED);
        assertThat(entity.getReason()).isEqualTo("Not compliant");
    }

    @Test
    void shouldSuspendSubscription() {
        SubscriptionEntity entity = buildSubscription(SubStatus.APPROVED);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.suspendSubscription(entity.getId(), "Abuse detected");

        assertThat(response.getStatus()).isEqualTo(SubStatus.SUSPENDED);
    }

    @Test
    void shouldResumeSubscription() {
        SubscriptionEntity entity = buildSubscription(SubStatus.SUSPENDED);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.resumeSubscription(entity.getId(), "Issue resolved");

        assertThat(response.getStatus()).isEqualTo(SubStatus.APPROVED);
    }

    @Test
    void shouldRevokeSubscription() {
        SubscriptionEntity entity = buildSubscription(SubStatus.APPROVED);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = subscriptionService.revokeSubscription(entity.getId(), "Contract ended");

        assertThat(response.getStatus()).isEqualTo(SubStatus.CANCELLED);
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        SubscriptionEntity entity = buildSubscription(SubStatus.REJECTED);

        when(subscriptionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> subscriptionService.approveSubscription(entity.getId(), "Try again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition");
    }
}
