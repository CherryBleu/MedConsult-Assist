package com.medconsult.common.mq;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式操作审计（架构文档 §7.5 / 《修改建议》§2.2）。
 *
 * <p>标在 Controller 写方法或类上，由 {@link AuditLogAspect} 在方法<b>成功返回后</b>
 * 产生 {@link AuditLogEvent}，经 {@link AuditLogProducer} 落本地消息表异步投递。
 * 方法抛异常则不审计（业务失败不记录为成功操作）。
 *
 * <p>用法示例：
 * <pre>
 * &#64;PostMapping
 * &#64;AuditLog(resourceType = "PRESCRIPTION", action = "CREATE", resourceIdFrom = ResourceIdFrom.RESULT)
 * public Result&lt;CreateResponse&gt; create(&#64;RequestBody CreateRequest req) { ... }
 *
 * &#64;PostMapping("/{prescriptionId}/review")
 * &#64;AuditLog(resourceType = "PRESCRIPTION", action = "UPDATE", resourceIdFrom = ResourceIdFrom.PATH)
 * public Result&lt;...&gt; review(&#64;PathVariable String prescriptionId, ...) { ... }
 * </pre>
 *
 * @see AuditLogAspect
 * @see ResourceIdFrom
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 资源类型：PATIENT/MEDICAL_RECORD/PRESCRIPTION/DRUG/SCHEDULE...（必填，audit_log.resource_type） */
    String resourceType();

    /** 操作类型：CREATE/UPDATE/DELETE/EXPORT/LOGIN/LOGOUT（必填，audit_log.action） */
    String action();

    /**
     * resourceId 取值来源。默认 PATH（从路径变量 recordId/prescriptionId 取）。
     * 新增类接口（create）返回值里才有编号，用 RESULT。
     * 不需要资源编号用 NONE。
     */
    ResourceIdFrom resourceIdFrom() default ResourceIdFrom.PATH;

    /** 资源名称（可选，冗余便于检索；不填则留空） */
    String resourceName() default "";

    /**
     * resourceId 路径变量名（resourceIdFrom=PATH 时生效，默认按 recordId/prescriptionId 顺序查找）。
     */
    String pathVarName() default "";
}
