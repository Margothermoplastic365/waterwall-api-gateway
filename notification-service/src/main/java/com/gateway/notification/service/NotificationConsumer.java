package com.gateway.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.channel.NotificationRouter;
import com.gateway.notification.dto.NotificationEvent;
import com.gateway.notification.entity.NotificationEntity;
import com.gateway.notification.entity.NotificationPreferenceEntity;
import com.gateway.notification.repository.NotificationPreferenceRepository;
import com.gateway.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final EmailService emailService;
    private final WebhookService webhookService;
    private final NotificationRouter notificationRouter;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationRepository notificationRepository,
                                NotificationPreferenceRepository notificationPreferenceRepository,
                                EmailService emailService,
                                WebhookService webhookService,
                                NotificationRouter notificationRouter,
                                ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.emailService = emailService;
        this.webhookService = webhookService;
        this.notificationRouter = notificationRouter;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{notificationQueue.name}")
    @Transactional
    public void handleNotification(String message) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
            log.info("Received notification event: eventId={} type={} channel={}",
                    event.getEventId(), event.getEventType(), event.getChannel());

            // Look up user preferences
            NotificationPreferenceEntity prefs = resolvePreferences(event.getRecipientUserId());

            // If eventType is in mutedEventTypes, skip entirely
            if (prefs.getMutedEventTypes() != null
                    && event.getEventType() != null
                    && prefs.getMutedEventTypes().contains(event.getEventType())) {
                log.info("Notification skipped: eventType={} is muted for userId={}",
                        event.getEventType(), event.getRecipientUserId());
                return;
            }

            String channel = event.getChannel() != null ? event.getChannel().toUpperCase() : "INAPP";

            switch (channel) {
                case "EMAIL" -> handleEmailNotification(event, prefs);
                case "WEBHOOK" -> handleWebhookNotification(event, prefs);
                case "INAPP" -> handleInAppNotification(event, prefs);
                default -> {
                    log.warn("Unknown channel '{}', defaulting to INAPP", channel);
                    handleInAppNotification(event, prefs);
                }
            }

            // Also dispatch to webhooks for any event if the user has registered endpoints
            if (!"WEBHOOK".equals(channel) && event.getRecipientUserId() != null && prefs.isWebhookEnabled()) {
                try {
                    webhookService.deliverWebhook(
                            event.getRecipientUserId(),
                            event.getEventType(),
                            event.getVariables()
                    );
                } catch (Exception ex) {
                    log.debug("Webhook delivery skipped or failed for userId={}: {}",
                            event.getRecipientUserId(), ex.getMessage());
                }
            }

            // Fan-out to multi-channel notification router (Slack, Teams, generic webhook)
            routeToExternalChannels(event);
        } catch (Exception ex) {
            log.error("Failed to process notification message: {}", ex.getMessage(), ex);
        }
    }

    private NotificationPreferenceEntity resolvePreferences(String recipientUserId) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            return defaultPreferences();
        }
        try {
            UUID userId = UUID.fromString(recipientUserId);
            return notificationPreferenceRepository.findByUserId(userId)
                    .orElse(defaultPreferences());
        } catch (IllegalArgumentException e) {
            return defaultPreferences();
        }
    }

    private NotificationPreferenceEntity defaultPreferences() {
        return NotificationPreferenceEntity.builder()
                .emailEnabled(true)
                .inAppEnabled(true)
                .webhookEnabled(true)
                .build();
    }

    private void handleWebhookNotification(NotificationEvent event, NotificationPreferenceEntity prefs) {
        if (prefs.isWebhookEnabled() && event.getRecipientUserId() != null) {
            webhookService.deliverWebhook(
                    event.getRecipientUserId(),
                    event.getEventType(),
                    event.getVariables()
            );
        } else {
            log.debug("Webhook delivery disabled for userId={}", event.getRecipientUserId());
        }
        // Also save as in-app notification for record keeping
        if (prefs.isInAppEnabled()) {
            saveNotification(event, "WEBHOOK");
        }
    }

    private void handleEmailNotification(NotificationEvent event, NotificationPreferenceEntity prefs) {
        if (prefs.isEmailEnabled()) {
            emailService.sendEmail(
                    event.getRecipientEmail(),
                    event.getTemplateName(),
                    event.getVariables()
            );
        } else {
            log.debug("Email delivery disabled for userId={}", event.getRecipientUserId());
        }

        // Also save as in-app notification for record keeping
        if (prefs.isInAppEnabled()) {
            saveNotification(event, "EMAIL");
        }
    }

    private void handleInAppNotification(NotificationEvent event, NotificationPreferenceEntity prefs) {
        if (prefs.isInAppEnabled()) {
            saveNotification(event, "INAPP");
        } else {
            log.debug("In-app notification disabled for userId={}", event.getRecipientUserId());
        }
    }

    private void saveNotification(NotificationEvent event, String type) {
        try {
            NotificationEntity entity = new NotificationEntity();
            entity.setUserId(UUID.fromString(event.getRecipientUserId()));
            entity.setTitle(resolveTitle(event));
            entity.setBody(resolveBody(event));
            entity.setType(type);
            entity.setRead(false);

            notificationRepository.save(entity);
            log.debug("Saved notification for userId={} type={}", event.getRecipientUserId(), type);
        } catch (Exception ex) {
            log.error("Failed to save notification for userId={}: {}",
                    event.getRecipientUserId(), ex.getMessage(), ex);
        }
    }

    private String resolveTitle(NotificationEvent event) {
        if (event.getVariables() != null && event.getVariables().containsKey("title")) {
            return event.getVariables().get("title").toString();
        }
        return event.getEventType() != null ? event.getEventType() : "Notification";
    }

    private String resolveBody(NotificationEvent event) {
        if (event.getVariables() != null && event.getVariables().containsKey("body")) {
            return event.getVariables().get("body").toString();
        }
        return "";
    }

    /**
     * Asynchronously fans out the event to all enabled external notification channels
     * (Slack, Teams, generic webhook) via the {@link NotificationRouter}.
     */
    private void routeToExternalChannels(NotificationEvent event) {
        try {
            String title = resolveTitle(event);
            String body = resolveBody(event);
            String severity = resolveSeverity(event);
            Map<String, Object> metadata = event.getVariables() != null
                    ? event.getVariables()
                    : Map.of();

            notificationRouter.route(title, body, severity, metadata);
        } catch (Exception ex) {
            log.debug("External channel routing failed for eventId={}: {}",
                    event.getEventId(), ex.getMessage());
        }
    }

    private String resolveSeverity(NotificationEvent event) {
        if (event.getVariables() != null && event.getVariables().containsKey("severity")) {
            return event.getVariables().get("severity").toString();
        }
        return "INFO";
    }
}
