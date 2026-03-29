package com.gateway.identity.service;

import com.gateway.common.auth.SecurityContextHelper;
import com.gateway.identity.entity.AuditEventEntity;
import com.gateway.identity.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service responsible for recording audit trail entries.
 * Populates actor information from the current SecurityContext and MDC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Log an audit event with actor information derived from the security context.
     *
     * @param action       the action performed (e.g. "user.login", "user.register")
     * @param resourceType the type of resource affected (e.g. "USER")
     * @param resourceId   the identifier of the affected resource
     * @param result       the outcome of the action (e.g. "SUCCESS", "FAILURE")
     */
    public void logEvent(String action, String resourceType, String resourceId, String result) {
        try {
            String currentUserId = SecurityContextHelper.getCurrentUserId();
            UUID actorId = currentUserId != null ? UUID.fromString(currentUserId) : null;

            AuditEventEntity event = AuditEventEntity.builder()
                    .actorId(actorId)
                    .actorEmail(resolveActorEmail())
                    .actorIp(MDC.get("clientIp"))
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .result(result)
                    .traceId(MDC.get("traceId"))
                    .build();

            auditEventRepository.save(event);
            log.debug("Audit event recorded: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result);
        } catch (Exception ex) {
            // Audit logging must never break the main flow
            log.error("Failed to record audit event: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result, ex);
        }
    }

    /**
     * Overloaded convenience method that also accepts the actor's email address
     * explicitly (useful during login when the SecurityContext is not yet populated).
     */
    public void logEvent(String action, String resourceType, String resourceId,
                         String result, String actorEmail, UUID actorId) {
        try {
            AuditEventEntity event = AuditEventEntity.builder()
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .actorIp(MDC.get("clientIp"))
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .result(result)
                    .traceId(MDC.get("traceId"))
                    .build();

            auditEventRepository.save(event);
            log.debug("Audit event recorded: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result);
        } catch (Exception ex) {
            log.error("Failed to record audit event: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result, ex);
        }
    }

    private String resolveActorEmail() {
        // The email is not directly in GatewayAuthentication; fall back to MDC
        // which can be populated by the authentication filter.
        return MDC.get("actorEmail");
    }
}
