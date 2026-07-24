package com.medconsult.common.mq.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a successful business write that must enqueue an audit event.
 *
 * <p>Expression attributes use Spring SpEL. Available variables:
 * {@code #result}, {@code #args}, {@code #p0/#a0...}, parameter names when
 * available, and {@code #methodName}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    String resourceType();

    String action();

    String resourceId();

    String resourceName() default "";

    String operatorId() default "";

    String operatorRole() default "";

    String operatorName() default "";

    String targetOwnerId() default "";

    String detail() default "";
}
