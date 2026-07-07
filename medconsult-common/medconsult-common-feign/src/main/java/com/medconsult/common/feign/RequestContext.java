package com.medconsult.common.feign;

import com.medconsult.common.security.JwtPayload;

/**
 * Feign 调用链上下文（架构文档 §2.4 / §3.2）。
 *
 * <p>承载当前请求的身份与链路元数据，供 {@link AuthRelayInterceptor} 决定注入哪种 Token：
 * <ul>
 *   <li>用户触发链路（HTTP 请求带用户身份）→ 透传用户 Token</li>
 *   <li>自动/无用户链路（定时任务、MQ 消费、回调）→ 注入服务自身 Token</li>
 * </ul>
 *
 * <p>身份信息复用 common-security 的 {@link com.medconsult.common.security.SecurityContext}，
 * 避免重复实现 request-scope 存储（也避免本模块直接依赖 Servlet API）。
 * traceId 与 callerService 走 ThreadLocal（调用链短生命周期，Feign 同步调用期间有效），
 * 对虚拟线程友好（虚拟线程是守护线程，ThreadLocal 正常工作）。
 */
public final class RequestContext {

    /** 调用方服务名（ThreadLocal，Feign 同步调用链内有效） */
    private static final ThreadLocal<String> CALLER_SERVICE = new ThreadLocal<>();
    /** 当前请求的原始用户 token（透传用，ThreadLocal） */
    private static final ThreadLocal<String> USER_TOKEN = new ThreadLocal<>();
    /** traceId（ThreadLocal，优先由 TraceIdFilter 设置的 MDC 读取） */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private RequestContext() {}

    /**
     * 当前请求是否由用户触发（SecurityContext 有用户身份）。
     */
    public static boolean isUserTriggered() {
        return currentUser() != null;
    }

    /**
     * 取当前用户身份。委托 SecurityContext（request-scope）。
     */
    public static JwtPayload currentUser() {
        return com.medconsult.common.security.SecurityContext.getPayload();
    }

    public static void setCallerService(String service) { CALLER_SERVICE.set(service); }
    public static String getCallerService() { return CALLER_SERVICE.get(); }
    public static void clearCallerService() { CALLER_SERVICE.remove(); }

    public static void setUserToken(String token) { USER_TOKEN.set(token); }
    public static String getUserToken() { return USER_TOKEN.get(); }
    public static void clearUserToken() { USER_TOKEN.remove(); }

    public static void setTraceId(String traceId) { TRACE_ID.set(traceId); }
    public static String getTraceId() {
        String t = TRACE_ID.get();
        if (t != null) return t;
        // 兜底：从 MDC 读（TraceIdFilter 写入）
        return org.slf4j.MDC.get("traceId");
    }
    public static void clearTraceId() { TRACE_ID.remove(); }

    /**
     * 清理所有 ThreadLocal（建议在请求结束时调用，防线程复用泄漏）。
     */
    public static void clearAll() {
        clearCallerService();
        clearUserToken();
        clearTraceId();
    }
}
