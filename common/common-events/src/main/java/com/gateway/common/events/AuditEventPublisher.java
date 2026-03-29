package com.gateway.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Convenience component for publishing {@link AuditEvent} instances to the
 * {@code audit.events} topic exchange. Automatically populates actor information
 * from the current SecurityContext (via MDC) so callers don't have to.
 *
 * <p>This component is available in every service that depends on {@code common-events}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish an audit event with actor information auto-populated from MDC.
     *
     * @param action       the action performed
     * @param resourceType the type of resource affected
     * @param resourceId   the identifier of the affected resource
     * @param result       the outcome of the action
     */
    public void publish(String action, String resourceType, String resourceId, String result) {
        publish(action, resourceType, resourceId, result, null, null);
    }

    /**
     * Publish an audit event with before/after state for change tracking.
     *
     * @param action       the action performed
     * @param resourceType the type of resource affected
     * @param resourceId   the identifier of the affected resource
     * @param result       the outcome of the action
     * @param beforeState  the resource state before the action (may be null)
     * @param afterState   the resource state after the action (may be null)
     */
    public void publish(String action, String resourceType, String resourceId,
                        String result, Object beforeState, Object afterState) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType("audit." + action)
                    .actorId(MDC.get("userId"))
                    .actorEmail(MDC.get("actorEmail"))
                    .actorIp(MDC.get("clientIp"))
                    .traceId(MDC.get("traceId"))
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .beforeState(beforeState)
                    .afterState(afterState)
                    .result(result)
                    .build();

            String routingKey = "audit." + resourceType.toLowerCase() + "." + action;

            rabbitTemplate.convertAndSend(
                    RabbitMQExchanges.AUDIT_EVENTS,
                    routingKey,
                    event
            );

            log.debug("Published audit event: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result);
        } catch (Exception ex) {
            // Audit publishing must never break the main flow
            log.error("Failed to publish audit event: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result, ex);
        }
    }
}
