package com.gateway.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.entity.WebhookEndpointEntity;
import com.gateway.notification.repository.WebhookDeliveryLogRepository;
import com.gateway.notification.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryLogRepository webhookDeliveryLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WebhookService webhookService;

    @Test
    void deliverWebhook_invalidUserId_returnsEarly() {
        webhookService.deliverWebhook("not-a-uuid", "test.event", Map.of("key", "val"));

        verifyNoInteractions(webhookEndpointRepository);
    }

    @Test
    void deliverWebhook_noActiveEndpoints_returnsEarly() {
        UUID userId = UUID.randomUUID();
        when(webhookEndpointRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        webhookService.deliverWebhook(userId.toString(), "test.event", Map.of());

        verify(webhookEndpointRepository).findByUserIdAndActiveTrue(userId);
        verifyNoInteractions(webhookDeliveryLogRepository);
    }

    @Test
    void deliverWebhook_validUser_looksUpEndpoints() {
        UUID userId = UUID.randomUUID();
        when(webhookEndpointRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        webhookService.deliverWebhook(userId.toString(), "api.created", Map.of("name", "PetStore"));

        verify(webhookEndpointRepository).findByUserIdAndActiveTrue(userId);
    }
}
