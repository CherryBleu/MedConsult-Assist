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

    /** request attribute key（JwtAuthServletFilter 直接用此 key 写 request；公开供其他 filter 读取，如限流过滤器） */
    public static final String PAYLOAD_ATTR_KEY = SecurityContext.class.getName() + ".payload";

    private SecurityContext() {
    }

    /**
     * 绑定当前请求的身份。
     * <p>注意：本方法依赖 {@link RequestContextHolder}，只在 dispatcher servlet 处理阶段可用
     * （即 controller / service / @Permission 切面里）。在 filter 阶段请直接用
     * {@code request.setAttribute(PAYLOAD_ATTR_KEY, payload)}——filter 在 dispatcher servlet 之前执行，
     * 此时 RequestContextHolder 尚未初始化。
     */
    public static void setPayload(JwtPayload payload) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            sra.getRequest().setAttribute(PAYLOAD_ATTR_KEY, payload);
        }
    }

    /**
     * 取当前请求身份。无身份（未登录或匿名）返回 null。
     */
    public static JwtPayload getPayload() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            Object v = sra.getRequest().getAttribute(PAYLOAD_ATTR_KEY);
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
     * 取当前服务身份并校验服务权限点，无服务身份抛 UNAUTHORIZED，scope 不足抛 FORBIDDEN。
     */
    public static JwtPayload requireService(String permissionCode) {
        JwtPayload p = requireService();
        if (permissionCode != null && !permissionCode.isBlank() && !p.hasPermission(permissionCode)) {
            throw new com.medconsult.common.core.BusinessException(
                    com.medconsult.common.core.ErrorCode.FORBIDDEN, "无服务访问权限: " + permissionCode);
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
