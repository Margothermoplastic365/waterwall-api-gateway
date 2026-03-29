package com.gateway.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative permission check.  Annotated methods require the current
 * principal to hold the specified permission(s).
 * <p>
 * When {@link #value()} is set, the user must hold that exact permission.
 * When {@link #any()} is set, the user must hold <em>at least one</em> of the
 * listed permissions (OR logic).  Both may be combined — the check succeeds
 * when the user has {@code value()} <strong>or</strong> any entry in
 * {@code any()}.
 * <p>
 * Users with the {@code SUPER_ADMIN} role bypass all permission checks.
 *
 * @see PermissionAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The required permission, e.g. {@code "api:create"}.
     */
    String value() default "";

    /**
     * Alternative permissions — the check passes if the user holds
     * <em>any one</em> of these (OR logic).
     */
    String[] any() default {};
}
