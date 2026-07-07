package com.medconsult.common.security;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * {@link Permission} 注解切面（架构文档 §4.3）。
 *
 * <p>切面职责（保持薄）：
 * <ul>
 *   <li>校验"已登录"（SecurityContext 非空）</li>
 *   <li>校验 {@code roles}（若声明）</li>
 *   <li>校验 {@code code} 是否在 scope</li>
 *   <li>具体数据范围过滤交给业务层（切面只读取注解，不做 SQL 改写）</li>
 * </ul>
 *
 * <p>角色/权限缓存（架构文档 §7.1 {@code role:{roleId}:perms}）由 auth-service 维护，
 * 写入 JWT scope；本切面只读 JWT 内的 scope，不再远程查 auth-service——避免每次鉴权都走 Feign。
 */
@Aspect
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    @Autowired(required = false)
    private PermissionChecker customChecker;

    /**
     * 可选的扩展校验器：业务服务可注入自定义逻辑（如查 Redis 黑名单、二次校验数据归属）。
     * 默认无，仅靠 JWT scope。
     */
    public interface PermissionChecker {
        /**
         * @param payload 当前身份
         * @param perm    命中的注解
         * @return true 放行；false 拒绝（抛 FORBIDDEN）
         */
        boolean check(JwtPayload payload, Permission perm);
    }

    @Around("@annotation(perm) || @within(perm)")
    public Object around(ProceedingJoinPoint pjp, Permission perm) throws Throwable {
        // 注解可能来自方法或类；优先取方法上的（更精确）
        Permission effective = resolveEffective(pjp, perm);

        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或 Token 失效");
        }

        // 1. 角色校验（可选）
        if (effective.roles().length > 0) {
            Set<String> have = payload.roles() == null ? Set.of() : Set.copyOf(payload.roles());
            boolean roleOk = Arrays.stream(effective.roles()).anyMatch(have::contains);
            if (!roleOk) {
                log.warn("权限拒绝[角色不足]: need={}, have={}", effective.roles(), payload.roles());
                throw new BusinessException(ErrorCode.FORBIDDEN, "角色权限不足");
            }
        }

        // 2. 权限点校验
        if (!effective.code().isBlank()) {
            if (!payload.hasPermission(effective.code())) {
                log.warn("权限拒绝[scope不足]: need={}, scopes={}", effective.code(), payload.scope());
                throw new BusinessException(ErrorCode.FORBIDDEN, "无访问权限: " + effective.code());
            }
        }

        // 3. 扩展校验器（可选）
        if (customChecker != null && !customChecker.check(payload, effective)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "访问范围校验失败");
        }

        return pjp.proceed();
    }

    /**
     * 方法上的 @Permission 优先于类上的。当前 effective 已是 Spring 选中的那个，
     * 但若来自 @within（类级），尝试回退到方法级覆盖。
     */
    private Permission resolveEffective(ProceedingJoinPoint pjp, Permission candidate) {
        if (pjp.getSignature() instanceof MethodSignature ms) {
            Method m = ms.getMethod();
            Permission methodPerm = AnnotationUtils.findAnnotation(m, Permission.class);
            if (methodPerm != null) {
                return methodPerm;
            }
        }
        return candidate;
    }
}
