package com.gateway.notification.controller;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.notification.dto.CreateWebhookRequest;
import com.gateway.notification.dto.WebhookResponse;
import com.gateway.notification.entity.WebhookDeliveryLogEntity;
import com.gateway.notification.entity.WebhookEndpointEntity;
import com.gateway.notification.repository.WebhookDeliveryLogRepository;
import com.gateway.notification.repository.WebhookEndpointRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * REST API for managing webhook endpoints. Authenticated users can register,
 * list, and delete their own webhook endpoints.
 */
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryLogRepository webhookDeliveryLogRepository;

    /**
     * Create a new webhook endpoint for the current user.
     */
    @PostMapping
    public ResponseEntity<WebhookResponse> createWebhook(
            @Valid @RequestBody CreateWebhookRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(httpRequest);

        String secret = request.getSecret();
        if (secret == null || secret.isBlank()) {
            secret = generateSecret();
        }

        WebhookEndpointEntity entity = WebhookEndpointEntity.builder()
                .userId(userId)
                .url(request.getUrl())
                .secret(secret)
                .active(true)
                .build();

        entity = webhookEndpointRepository.save(entity);

        WebhookResponse response = toResponse(entity);
        return ResponseEntity
                .created(URI.create("/v1/webhooks/" + entity.getId()))
                .body(response);
    }

    /**
     * List all webhook endpoints for the current user.
     */
    @GetMapping
    public ResponseEntity<List<WebhookResponse>> listWebhooks(HttpServletRequest httpRequest) {
        UUID userId = resolveUserId(httpRequest);

        List<WebhookResponse> webhooks = webhookEndpointRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(webhooks);
    }

    /**
     * Delete a webhook endpoint by ID (must belong to the current user).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(httpRequest);

        return webhookEndpointRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(e -> {
                    webhookEndpointRepository.delete(e);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List delivery logs for a webhook endpoint (paginated).
     */
    @GetMapping("/{id}/deliveries")
    public ResponseEntity<Page<WebhookDeliveryLogEntity>> listDeliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(httpRequest);

        // Verify the webhook endpoint belongs to the current user
        return webhookEndpointRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(e -> {
                    Pageable pageable = PageRequest.of(page, size);
                    Page<WebhookDeliveryLogEntity> deliveries =
                            webhookDeliveryLogRepository.findByWebhookEndpointIdOrderByDeliveredAtDesc(id, pageable);
                    return ResponseEntity.ok(deliveries);
                })
                .orElse(ResponseEntity.notFound().build());
    }

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

    private WebhookResponse toResponse(WebhookEndpointEntity entity) {
        return WebhookResponse.builder()
                .id(entity.getId())
                .url(entity.getUrl())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }
}
