package com.gateway.management.controller;

import com.gateway.management.dto.CreateSubscriptionRequest;
import com.gateway.management.dto.ReviewSubscriptionRequest;
import com.gateway.management.dto.SubscriptionResponse;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void subscribe_returnsCreated() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setApplicationId(UUID.randomUUID());
        request.setApiId(UUID.randomUUID());
        request.setPlanId(UUID.randomUUID());

        SubscriptionResponse expected = SubscriptionResponse.builder()
                .id(UUID.randomUUID())
                .status(SubStatus.PENDING)
                .build();
        when(subscriptionService.subscribe(request)).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.subscribe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).subscribe(request);
    }

    @Test
    void listSubscriptions_returnsOk() {
        UUID appId = UUID.randomUUID();
        UUID apiId = UUID.randomUUID();
        String status = "APPROVED";
        List<SubscriptionResponse> subs = List.of(
                SubscriptionResponse.builder().id(UUID.randomUUID()).build()
        );
        when(subscriptionService.listSubscriptions(appId, apiId, status)).thenReturn(subs);

        ResponseEntity<List<SubscriptionResponse>> response =
                subscriptionController.listSubscriptions(appId, apiId, status);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(subscriptionService).listSubscriptions(appId, apiId, status);
    }

    @Test
    void listSubscriptions_withNullParams_returnsOk() {
        List<SubscriptionResponse> subs = List.of();
        when(subscriptionService.listSubscriptions(null, null, null)).thenReturn(subs);

        ResponseEntity<List<SubscriptionResponse>> response =
                subscriptionController.listSubscriptions(null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void approveSubscription_returnsOk() {
        UUID id = UUID.randomUUID();
        ReviewSubscriptionRequest request = new ReviewSubscriptionRequest();
        request.setReason("Looks good");
        SubscriptionResponse expected = SubscriptionResponse.builder()
                .id(id)
                .status(SubStatus.APPROVED)
                .build();
        when(subscriptionService.approveSubscription(id, "Looks good")).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.approve(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).approveSubscription(id, "Looks good");
    }

    @Test
    void approveSubscription_withNullRequest_returnsOk() {
        UUID id = UUID.randomUUID();
        SubscriptionResponse expected = SubscriptionResponse.builder().id(id).build();
        when(subscriptionService.approveSubscription(id, null)).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.approve(id, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(subscriptionService).approveSubscription(id, null);
    }

    @Test
    void rejectSubscription_returnsOk() {
        UUID id = UUID.randomUUID();
        ReviewSubscriptionRequest request = new ReviewSubscriptionRequest();
        request.setReason("Does not meet requirements");
        SubscriptionResponse expected = SubscriptionResponse.builder()
                .id(id)
                .status(SubStatus.REJECTED)
                .build();
        when(subscriptionService.rejectSubscription(id, "Does not meet requirements")).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.reject(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).rejectSubscription(id, "Does not meet requirements");
    }

    @Test
    void revokeSubscription_returnsOk() {
        UUID id = UUID.randomUUID();
        ReviewSubscriptionRequest request = new ReviewSubscriptionRequest();
        request.setReason("Violation");
        SubscriptionResponse expected = SubscriptionResponse.builder()
                .id(id)
                .status(SubStatus.CANCELLED)
                .build();
        when(subscriptionService.revokeSubscription(id, "Violation")).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.revoke(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).revokeSubscription(id, "Violation");
    }

    @Test
    void suspendSubscription_returnsOk() {
        UUID id = UUID.randomUUID();
        ReviewSubscriptionRequest request = new ReviewSubscriptionRequest();
        request.setReason("Maintenance");
        SubscriptionResponse expected = SubscriptionResponse.builder().id(id).build();
        when(subscriptionService.suspendSubscription(id, "Maintenance")).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.suspend(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).suspendSubscription(id, "Maintenance");
    }

    @Test
    void resumeSubscription_returnsOk() {
        UUID id = UUID.randomUUID();
        SubscriptionResponse expected = SubscriptionResponse.builder().id(id).build();
        when(subscriptionService.resumeSubscription(id, null)).thenReturn(expected);

        ResponseEntity<SubscriptionResponse> response = subscriptionController.resume(id, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(subscriptionService).resumeSubscription(id, null);
    }

    @Test
    void unsubscribe_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = subscriptionController.unsubscribe(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(subscriptionService).unsubscribe(id);
    }
}
