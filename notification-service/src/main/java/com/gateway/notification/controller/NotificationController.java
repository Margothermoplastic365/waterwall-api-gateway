package com.gateway.notification.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.notification.dto.NotificationPreferenceRequest;
import com.gateway.notification.dto.NotificationResponse;
import com.gateway.notification.entity.NotificationEntity;
import com.gateway.notification.entity.NotificationPreferenceEntity;
import com.gateway.notification.repository.NotificationPreferenceRepository;
import com.gateway.notification.repository.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    /**
     * List notifications for the current user (paginated, newest first).
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        UUID userId = resolveUserId(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationEntity> entities = notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable);

        Page<NotificationResponse> response = entities.map(this::toResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Get the unread notification count for the current user.
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> unreadCount(HttpServletRequest request) {
        UUID userId = resolveUserId(request);
        long count = notificationRepository.countByUserIdAndReadFalse(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            HttpServletRequest request) {

        UUID userId = resolveUserId(request);

        return notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .map(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                    return ResponseEntity.ok(toResponse(n));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark all notifications as read for the current user.
     */
    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllAsRead(HttpServletRequest request) {
        UUID userId = resolveUserId(request);
        int updated = notificationRepository.markAllAsReadByUserId(userId);
        return ResponseEntity.ok(Map.of("markedAsRead", updated));
    }

    /**
     * Get notification preferences for the current user.
     */
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceEntity> getPreferences(HttpServletRequest request) {
        UUID userId = resolveUserId(request);
        NotificationPreferenceEntity prefs = notificationPreferenceRepository.findByUserId(userId)
                .orElse(NotificationPreferenceEntity.builder()
                        .userId(userId)
                        .emailEnabled(true)
                        .inAppEnabled(true)
                        .webhookEnabled(true)
                        .mutedEventTypes(new ArrayList<>())
                        .build());
        return ResponseEntity.ok(prefs);
    }

    /**
     * Update notification preferences for the current user.
     */
    @PutMapping("/preferences")
    @Transactional
    public ResponseEntity<NotificationPreferenceEntity> updatePreferences(
            @RequestBody NotificationPreferenceRequest preferenceRequest,
            HttpServletRequest request) {
        UUID userId = resolveUserId(request);
        NotificationPreferenceEntity prefs = notificationPreferenceRepository.findByUserId(userId)
                .orElse(NotificationPreferenceEntity.builder()
                        .userId(userId)
                        .build());

        prefs.setEmailEnabled(preferenceRequest.isEmailEnabled());
        prefs.setInAppEnabled(preferenceRequest.isInAppEnabled());
        prefs.setWebhookEnabled(preferenceRequest.isWebhookEnabled());
        prefs.setMutedEventTypes(preferenceRequest.getMutedEventTypes() != null
                ? preferenceRequest.getMutedEventTypes() : new ArrayList<>());

        prefs = notificationPreferenceRepository.save(prefs);
        return ResponseEntity.ok(prefs);
    }

    /**
     * Resolves the current user ID from the security context, falling back to
     * the X-User-Id header for internal service-to-service calls.
     */
    private UUID resolveUserId(HttpServletRequest request) {
        String userId = SecurityContextHelper.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            userId = request.getHeader("X-User-Id");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Unable to determine current user ID");
        }
        return UUID.fromString(userId);
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .body(entity.getBody())
                .type(entity.getType())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
