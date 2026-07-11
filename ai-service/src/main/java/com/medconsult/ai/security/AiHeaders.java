package com.medconsult.ai.security;

/**
 * AI 服务专用 HTTP 头常量（架构文档 §2.4 / §7.4）。
 *
 * <p>替代 ai-stack common 的 {@code RequestContext} 头常量。backend/dev 的 common-feign
 * {@code AuthRelayInterceptor} 已转发 {@code X-Caller-Service} / {@code X-User-Id} / {@code X-Trace-Id}，
 * 这里只定义 AI 需要额外读取的请求头名，与 common-feign / common-web 保持一致。
 *
 * <p>这些头由网关 {@code JwtAuthFilter}（X-User-Id / X-User-Roles）或 Feign
 * {@code AuthRelayInterceptor}（X-Caller-Service / X-Trace-Id）注入，AI 服务只读取。
 */
public final class AiHeaders {

    private AiHeaders() {}

    /** 链路追踪 ID（与 common-web TraceIdFilter / common-feign AuthRelayInterceptor 一致） */
    public static final String TRACE_ID = "X-Trace-Id";

    /** 调用方服务编码（Feign AuthRelayInterceptor 注入，供审计/限流区分来源） */
    public static final String CALLER_SERVICE = "X-Caller-Service";

    /** 触发本次 AI 调用的用户 ID（网关 JwtAuthFilter 注入，限流/审计按用户维度） */
    public static final String USER_ID = "X-User-Id";

    /** 触发用户 ID 的别名（部分内部链路用此头传递，与 USER_ID 二选一读取） */
    public static final String TRIGGER_USER_ID = "X-Trigger-User-Id";
}
