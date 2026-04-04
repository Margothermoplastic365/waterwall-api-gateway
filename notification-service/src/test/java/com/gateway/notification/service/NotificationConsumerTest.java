package com.gateway.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.channel.NotificationRouter;
import com.gateway.notification.entity.NotificationEntity;
import com.gateway.notification.entity.NotificationPreferenceEntity;
import com.gateway.notification.repository.NotificationPreferenceRepository;
import com.gateway.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private WebhookService webhookService;

    @Mock
    private NotificationRouter notificationRouter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    // ── Email channel ───────────────────────────────────────────────────

    @Test
    void handleNotification_emailChannel_sendsEmailAndSavesInApp() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-1",
                    "eventType": "welcome",
                    "recipientUserId": "%s",
                    "recipientEmail": "user@example.com",
                    "templateName": "welcome",
                    "channel": "EMAIL",
                    "variables": {"name": "Alice"}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        verify(emailService).sendEmail(eq("user@example.com"), eq("welcome"), anyMap());
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    void handleNotification_emailDisabled_skipsEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-2",
                    "eventType": "welcome",
                    "recipientUserId": "%s",
                    "recipientEmail": "user@example.com",
                    "templateName": "welcome",
                    "channel": "EMAIL"
                }
                """, userId);

        NotificationPreferenceEntity prefs = defaultPrefs(userId);
        prefs.setEmailEnabled(false);
        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(prefs));

        notificationConsumer.handleNotification(json);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyMap());
    }

    // ── In-app channel ──────────────────────────────────────────────────

    @Test
    void handleNotification_inAppChannel_savesNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-3",
                    "eventType": "api.created",
                    "recipientUserId": "%s",
                    "channel": "INAPP",
                    "variables": {"title": "API Created", "body": "Your API is live."}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        NotificationEntity saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("API Created");
        assertThat(saved.getBody()).isEqualTo("Your API is live.");
        assertThat(saved.getType()).isEqualTo("INAPP");
    }

    @Test
    void handleNotification_inAppDisabled_doesNotSave() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-4",
                    "eventType": "test",
                    "recipientUserId": "%s",
                    "channel": "INAPP"
                }
                """, userId);

        NotificationPreferenceEntity prefs = defaultPrefs(userId);
        prefs.setInAppEnabled(false);
        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(prefs));

        notificationConsumer.handleNotification(json);

        verify(notificationRepository, never()).save(any());
    }

    // ── Webhook channel ─────────────────────────────────────────────────

    @Test
    void handleNotification_webhookChannel_deliversWebhook() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-5",
                    "eventType": "api.updated",
                    "recipientUserId": "%s",
                    "channel": "WEBHOOK",
                    "variables": {"key": "value"}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        verify(webhookService).deliverWebhook(eq(userId.toString()), eq("api.updated"), anyMap());
    }

    // ── Muted event types ───────────────────────────────────────────────

    @Test
    void handleNotification_mutedEventType_skipsEntirely() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-6",
                    "eventType": "api.updated",
                    "recipientUserId": "%s",
                    "channel": "EMAIL",
                    "recipientEmail": "test@example.com",
                    "templateName": "update"
                }
                """, userId);

        NotificationPreferenceEntity prefs = defaultPrefs(userId);
        prefs.setMutedEventTypes(List.of("api.updated", "api.deleted"));
        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(prefs));

        notificationConsumer.handleNotification(json);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyMap());
        verify(webhookService, never()).deliverWebhook(anyString(), anyString(), any());
        verify(notificationRepository, never()).save(any());
    }

    // ── Default channel (null) ──────────────────────────────────────────

    @Test
    void handleNotification_nullChannel_defaultsToInApp() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-7",
                    "eventType": "test.event",
                    "recipientUserId": "%s",
                    "variables": {"title": "Test"}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("INAPP");
    }

    @Test
    void handleNotification_unknownChannel_defaultsToInApp() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-8",
                    "eventType": "test.event",
                    "recipientUserId": "%s",
                    "channel": "SMS"
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    // ── External routing ────────────────────────────────────────────────

    @Test
    void handleNotification_routesToExternalChannels() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-9",
                    "eventType": "alert",
                    "recipientUserId": "%s",
                    "channel": "INAPP",
                    "variables": {"title": "Alert", "body": "Check now", "severity": "ERROR"}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        verify(notificationRouter).route(eq("Alert"), eq("Check now"), eq("ERROR"), anyMap());
    }

    // ── Webhook fanout for non-webhook channels ─────────────────────────

    @Test
    void handleNotification_emailChannel_alsoDeliversWebhookIfEnabled() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-10",
                    "eventType": "welcome",
                    "recipientUserId": "%s",
                    "recipientEmail": "user@example.com",
                    "templateName": "welcome",
                    "channel": "EMAIL"
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(defaultPrefs(userId)));

        notificationConsumer.handleNotification(json);

        // Primary email delivery
        verify(emailService).sendEmail(anyString(), anyString(), any());
        // Also fans out to webhook
        verify(webhookService).deliverWebhook(eq(userId.toString()), eq("welcome"), any());
    }

    @Test
    void handleNotification_webhookDisabled_doesNotFanOutWebhook() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-11",
                    "eventType": "welcome",
                    "recipientUserId": "%s",
                    "recipientEmail": "user@example.com",
                    "templateName": "welcome",
                    "channel": "EMAIL"
                }
                """, userId);

        NotificationPreferenceEntity prefs = defaultPrefs(userId);
        prefs.setWebhookEnabled(false);
        when(notificationPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(prefs));

        notificationConsumer.handleNotification(json);

        verify(webhookService, never()).deliverWebhook(anyString(), anyString(), any());
    }

    // ── Invalid JSON ────────────────────────────────────────────────────

    @Test
    void handleNotification_invalidJson_doesNotThrow() {
        notificationConsumer.handleNotification("not valid json");

        verifyNoInteractions(emailService);
        verifyNoInteractions(webhookService);
        verifyNoInteractions(notificationRepository);
    }

    // ── No user preferences ─────────────────────────────────────────────

    @Test
    void handleNotification_noPreferences_usesDefaults() throws Exception {
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                    "eventId": "evt-12",
                    "eventType": "test",
                    "recipientUserId": "%s",
                    "channel": "INAPP",
                    "variables": {"title": "Hello"}
                }
                """, userId);

        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        notificationConsumer.handleNotification(json);

        // Default preferences have inApp enabled, so should save
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    void handleNotification_nullRecipientUserId_usesDefaultPreferences() throws Exception {
        String json = """
                {
                    "eventId": "evt-13",
                    "eventType": "system",
                    "channel": "INAPP",
                    "variables": {"title": "System Event"}
                }
                """;

        notificationConsumer.handleNotification(json);

        // Default prefs enable inApp; saveNotification will fail on UUID.fromString(null)
        // but exception is caught, so no propagation
        verifyNoInteractions(notificationPreferenceRepository);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private NotificationPreferenceEntity defaultPrefs(UUID userId) {
        return NotificationPreferenceEntity.builder()
                .userId(userId)
                .emailEnabled(true)
                .inAppEnabled(true)
                .webhookEnabled(true)
                .mutedEventTypes(List.of())
                .build();
    }
}
