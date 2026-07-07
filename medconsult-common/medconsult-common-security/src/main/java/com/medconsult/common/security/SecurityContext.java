package com.medconsult.common.security;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前请求身份持有器（架构文档 §4.3 / §2.4）。
 *
 * <p>基于 {@link RequestContextHolder}（请求作用域），无需 ThreadLocal，对虚拟线程友好。
 * 由 Token 解析过滤器（业务服务自行实现，或后续 common-security 补 AuthFilter）写入。
 *
 * <p>业务代码与 {@code @Permission} 切面通过本类读取当前身份，不直接碰 Servlet API。
 */
public final class SecurityContext {

    /** request attribute key */
    private static final String ATTR_PAYLOAD = SecurityContext.class.getName() + ".payload";

    private SecurityContext() {
    }

    /**
     * 绑定当前请求的身份（由鉴权过滤器调用）。
     */
    public static void setPayload(JwtPayload payload) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            sra.getRequest().setAttribute(ATTR_PAYLOAD, payload);
        }
    }

    /**
     * 取当前请求身份。无身份（未登录或匿名）返回 null。
     */
    public static JwtPayload getPayload() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            Object v = sra.getRequest().getAttribute(ATTR_PAYLOAD);
            return v instanceof JwtPayload jp ? jp : null;
        }
        return null;
    }

    /**
     * 取当前身份，无则抛 UNAUTHORIZED。
     */
    public static JwtPayload requireUser() {
        JwtPayload p = getPayload();
        if (p == null || !p.isUser()) {
            throw new com.medconsult.common.core.BusinessException(
                    com.medconsult.common.core.ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        return p;
    }

    /**
     * 取当前服务身份，无则抛 UNAUTHORIZED（/internal/* 鉴权用）。
     */
    public static JwtPayload requireService() {
        JwtPayload p = getPayload();
        if (p == null || !p.isService()) {
            throw new com.medconsult.common.core.BusinessException(
                    com.medconsult.common.core.ErrorCode.UNAUTHORIZED, "需要服务身份鉴权");
        }
        return p;
    }

    /**
     * 当前用户 ID（便捷）。非用户身份返回 null。
     */
    public static Long currentUserId() {
        JwtPayload p = getPayload();
        return p != null && p.isUser() ? p.userId() : null;
    }
}
