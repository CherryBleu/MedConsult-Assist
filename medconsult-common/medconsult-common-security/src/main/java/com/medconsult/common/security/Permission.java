package com.medconsult.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解（架构文档 §4.3）。
 *
 * <p>标在 Controller 方法或类上，由 {@link PermissionAspect} 切面校验：
 * <ol>
 *   <li>取当前身份（{@link SecurityContext}），无身份 → 401</li>
 *   <li>校验 {@link #code()} 是否在身份的 scope 中，不在 → 403</li>
 *   <li>{@link #dataScope()} 仅声明，实际查询过滤由业务层 + MyBatis 拦截器消费</li>
 * </ol>
 *
 * <p>用法：
 * <pre>
 *   &#64;Permission(code = "prescription:review", dataScope = DataScope.ALL)
 *   &#64;PostMapping("/prescriptions/{id}/review")
 *   public Result&lt;?&gt; review(...) { ... }
 * </pre>
 *
 * <p>用户与服务身份都走同一套 scope 校验（架构文档 §4.2 双模）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    /**
     * 权限点编码，如 {@code "prescription:review"}、{@code "patient:read"}。
     * 留空表示仅校验"已登录"，不校验具体权限点。
     */
    String code() default "";

    /**
     * 数据范围声明（ALL/DEPT/SELF/ASSIGNED）。仅文档/拦截器消费，切面本身不据此过滤。
     */
    DataScope dataScope() default DataScope.ALL;

    /**
     * 允许的角色码（可选，进一步收紧；不填则只看 scope）。
     * 示例：{@code roles = {"DOCTOR", "PHARMACY_ADMIN"}}。
     */
    String[] roles() default {};
}
