package com.gateway.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Platform-wide audit event published to the {@code audit.events} exchange.
 * Consumed by analytics-service for centralized audit storage and querying.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AuditEvent extends BaseEvent {

    /** Email address of the actor who triggered the event. */
    private String actorEmail;

    /** IP address of the actor. */
    private String actorIp;

    /** The action performed (e.g. "user.login", "api.publish", "subscription.approve"). */
    private String action;

    /** The type of resource affected (e.g. "USER", "API", "SUBSCRIPTION"). */
    private String resourceType;

    /** The identifier of the affected resource. */
    private String resourceId;

    /** Serialized state of the resource before the action (JSON string or null). */
    private Object beforeState;

    /** Serialized state of the resource after the action (JSON string or null). */
    private Object afterState;

    /** The outcome of the action (e.g. "SUCCESS", "FAILURE", "DENIED"). */
    private String result;
}
