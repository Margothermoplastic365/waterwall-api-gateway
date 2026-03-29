package com.gateway.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.notification.entity.NotificationEntity;
import com.gateway.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes platform-wide domain events from the {@code platform.events} topic exchange
 * and creates in-app notification records for relevant event types (API lifecycle,
 * subscription lifecycle).
 *
 * <p>For subscription events the consumer creates an additional notification for
 * the application/API owner (identified by {@code ownerId} in the event payload)
 * so that both the actor and the resource owner are kept informed.</p>
 */
@Service
public class PlatformEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlatformEventsConsumer.class);

    private static final Map<String, String> EVENT_TITLE_MAP = Map.ofEntries(
            Map.entry("api.published", "API Published"),
            Map.entry("api.deprecated", "API Deprecated"),
            Map.entry("api.retired", "API Retired"),
            Map.entry("api.deployed", "API Deployed"),
            Map.entry("subscription.created", "New Subscription"),
            Map.entry("subscription.approved", "Subscription Approved"),
            Map.entry("subscription.rejected", "Subscription Rejected"),
            Map.entry("subscription.suspended", "Subscription Suspended"),
            Map.entry("subscription.revoked", "Subscription Revoked")
    );

    private static final Set<String> SUBSCRIPTION_EVENTS = Set.of(
            "subscription.created",
            "subscription.approved",
            "subscription.rejected",
            "subscription.suspended",
            "subscription.revoked"
    );

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public PlatformEventsConsumer(NotificationRepository notificationRepository,
                                  ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{platformEventsQueue.name}")
    @Transactional
    public void handlePlatformEvent(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(
                    message, new TypeReference<Map<String, Object>>() {});

            String eventType = extractString(event, "eventType");
            String actorId = extractString(event, "actorId");
            String resourceId = extractString(event, "resourceId");

            if (eventType == null || eventType.isBlank()) {
                log.warn("Received platform event with missing eventType, skipping: {}", message);
                return;
            }

            log.info("Received platform event: eventType={} actorId={} resourceId={}",
                    eventType, actorId, resourceId);

            String title = EVENT_TITLE_MAP.get(eventType);
            if (title == null) {
                log.debug("Ignoring unhandled platform event type: {}", eventType);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = event.containsKey("payload")
                    ? (Map<String, Object>) event.get("payload")
                    : Map.of();

            String body = buildNotificationBody(eventType, payload, resourceId);

            // Notify the actor (the user who triggered the event)
            UUID actorUuid = resolveUserId(actorId);
            if (actorUuid != null) {
                saveNotification(actorUuid, title, body);
                log.info("Created notification for platform event: eventType={} userId={}",
                        eventType, actorUuid);
            } else {
                log.warn("Cannot create actor notification: invalid or missing actorId={} for eventType={}",
                        actorId, eventType);
            }

            // For subscription events, also notify the application/API owner
            if (SUBSCRIPTION_EVENTS.contains(eventType)) {
                notifyApplicationOwner(eventType, title, body, payload, actorUuid);
            }

        } catch (Exception ex) {
            log.error("Failed to process platform event message: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Notifies the application/API owner for subscription events.
     * The owner is identified by the {@code ownerId} field in the event payload.
     * A notification is only created when the owner differs from the actor to
     * avoid duplicate notifications.
     */
    private void notifyApplicationOwner(String eventType, String title, String body,
                                         Map<String, Object> payload, UUID actorUuid) {
        String ownerId = payload.containsKey("ownerId")
                ? payload.get("ownerId").toString()
                : null;

        UUID ownerUuid = resolveUserId(ownerId);
        if (ownerUuid == null) {
            log.debug("No valid ownerId in payload for subscription event: eventType={}", eventType);
            return;
        }

        // Avoid duplicate notification if the actor is also the owner
        if (ownerUuid.equals(actorUuid)) {
            log.debug("Owner is the same as actor, skipping duplicate notification: userId={}", ownerUuid);
            return;
        }

        saveNotification(ownerUuid, title, body);
        log.info("Created owner notification for subscription event: eventType={} ownerId={}",
                eventType, ownerUuid);
    }

    private void saveNotification(UUID userId, String title, String body) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setBody(body);
        entity.setType("PLATFORM_EVENT");
        entity.setRead(false);

        notificationRepository.save(entity);
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private UUID resolveUserId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            log.debug("'{}' is not a valid UUID", id);
            return null;
        }
    }

    private String buildNotificationBody(String eventType, Map<String, Object> payload,
                                          String resourceId) {
        String detail = "";
        if (payload.containsKey("name")) {
            detail = " '" + payload.get("name") + "'";
        } else if (resourceId != null && !resourceId.isBlank()) {
            detail = " [" + resourceId + "]";
        }

        return switch (eventType) {
            case "api.published"           -> "API" + detail + " has been published and is now available.";
            case "api.deprecated"          -> "API" + detail + " has been deprecated.";
            case "api.retired"             -> "API" + detail + " has been retired and is no longer available.";
            case "api.deployed"            -> "API" + detail + " has been deployed successfully.";
            case "subscription.created"    -> "A new subscription" + detail + " has been created.";
            case "subscription.approved"   -> "Subscription" + detail + " has been approved.";
            case "subscription.rejected"   -> "Subscription" + detail + " has been rejected.";
            case "subscription.suspended"  -> "Subscription" + detail + " has been suspended.";
            case "subscription.revoked"    -> "Subscription" + detail + " has been revoked.";
            default                        -> "Platform event: " + eventType;
        };
    }
}
