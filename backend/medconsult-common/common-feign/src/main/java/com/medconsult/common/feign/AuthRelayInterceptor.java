package com.medconsult.common.feign;

import com.medconsult.common.security.JwtPayload;
import feign.MethodMetadata;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign 鉴权透传拦截器（架构文档 §2.4 关键设计）。
 *
 * <p>双模：
 * <ul>
 *   <li><b>用户链路</b>（RequestContext.currentUser() 非空）：透传用户 JWT +
 *       X-User-Id / X-User-Roles，被调方审计挂在用户名下（医生开方→drug 审计记医生）</li>
 *   <li><b>服务/自动链路</b>（无用户，如定时任务/MQ 消费）：注入服务自身 JWT，
 *       透传 X-Caller-Service 作为审计/限流元数据，被调方按 SERVICE JWT 的 serviceCode/scope 鉴权</li>
 * </ul>
 *
 * <p>无论哪种模式，永远透传 X-Trace-Id（§7.4 链路追踪）。
 *
 * <p><b>注意</b>：本拦截器只负责"该放什么头"，不负责"token 从哪来"。
 * 用户 token 由业务服务的 AuthFilter 解析后写入 SecurityContext；
 * 服务 token 由 {@link ServiceTokenProvider} 提供（auth-service 换发，缓存）。
 */
public class AuthRelayInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthRelayInterceptor.class);

    public static final String HEADER_AUTH = "Authorization";
    public static final String HEADER_BEARER_PREFIX = "Bearer ";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_SCOPE = "X-User-Scope";
    public static final String HEADER_CALLER_SERVICE = "X-Caller-Service";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String SERVICE_TOKEN_BOOTSTRAP_PATH = "/internal/auth/service-token";

    private final ServiceTokenProvider serviceTokenProvider;

    public AuthRelayInterceptor(ServiceTokenProvider serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public void apply(RequestTemplate tpl) {
        // 1. 永远透传 traceId
        String traceId = RequestContext.getTraceId();
        if (traceId != null) {
            tpl.header(HEADER_TRACE_ID, traceId);
        }

        // 2. 透传 caller service（被调方审计用）
        String caller = RequestContext.getCallerService();
        if (caller != null) {
            tpl.header(HEADER_CALLER_SERVICE, caller);
        }

        // /internal/auth/service-token 是服务获取首个 SERVICE JWT 的 bootstrap 入口。
        // 此调用以 body 中的 serviceCode/apiKey 鉴权，不能再调用 ServiceTokenProvider，
        // 否则 AiServiceTokenProvider -> AuthFeignClient -> 本拦截器 -> provider.get() 会递归。
        if (isServiceTokenBootstrap(tpl)) {
            return;
        }

        // /internal/** 一律使用服务身份。即使当前请求由用户触发，下游 Controller 也会
        // 调 SecurityContext.requireService()，透传 X-User-* 会被判为非服务身份而 401。
        if (isInternalEndpoint(tpl)) {
            applyServiceToken(tpl);
            return;
        }

        // 3. 双模鉴权
        JwtPayload user = RequestContext.currentUser();
        if (user != null && user.isUser()) {
            // 用户链路：透传用户 token（从 RequestContext）+ 身份头
            String userToken = RequestContext.getUserToken();
            if (userToken != null) {
                tpl.removeHeader(HEADER_AUTH);
                tpl.header(HEADER_AUTH, HEADER_BEARER_PREFIX + userToken);
            }
            if (user.userId() != null) {
                tpl.header(HEADER_USER_ID, String.valueOf(user.userId()));
            }
            if (user.roles() != null && !user.roles().isEmpty()) {
                tpl.header(HEADER_USER_ROLES, String.join(",", user.roles()));
            }
            // 透传权限点列表（scope）：与网关 JwtAuthFilter 一致，避免下游业务服务
            // 重建 JwtPayload 时 scope 为空导致 @Permission 校验 403。
            if (user.scope() != null && !user.scope().isEmpty()) {
                tpl.header(HEADER_USER_SCOPE, String.join(",", user.scope()));
            }
        } else {
            // 服务/自动链路：注入服务自身 JWT
            applyServiceToken(tpl);
        }
    }

    private static boolean isServiceTokenBootstrap(RequestTemplate tpl) {
        String path = requestPath(tpl);
        return path != null && path.contains(SERVICE_TOKEN_BOOTSTRAP_PATH);
    }

    private static boolean isInternalEndpoint(RequestTemplate tpl) {
        String path = requestPath(tpl);
        return path != null && path.contains("/internal/");
    }

    private static String requestPath(RequestTemplate tpl) {
        String path = tpl.path();
        if (path == null || path.isBlank()) {
            path = tpl.url();
        }
        if (path == null || !path.contains("/internal/")) {
            MethodMetadata metadata = tpl.methodMetadata();
            RequestTemplate methodTemplate = metadata == null ? null : metadata.template();
            String methodPath = methodTemplate == null ? null : methodTemplate.path();
            if (methodPath == null || methodPath.isBlank()) {
                methodPath = methodTemplate == null ? null : methodTemplate.url();
            }
            if (methodPath != null && !methodPath.isBlank()) {
                path = path == null || path.isBlank() ? methodPath : path + " " + methodPath;
            }
        }
        if (path == null || !path.contains("/internal/")) {
            Target<?> target = tpl.feignTarget();
            String targetUrl = target == null ? null : target.url();
            if (targetUrl != null && !targetUrl.isBlank()) {
                path = path == null || path.isBlank() ? targetUrl : path + " " + targetUrl;
            }
        }
        return path;
    }

    private void applyServiceToken(RequestTemplate tpl) {
        String serviceToken = serviceTokenProvider.get();
        tpl.removeHeader(HEADER_AUTH);
        if (serviceToken != null && !serviceToken.isBlank()) {
            tpl.header(HEADER_AUTH, HEADER_BEARER_PREFIX + serviceToken);
        }
        if (log.isDebugEnabled()) {
            log.debug("Feign service auth applied path={} tokenPresent={}",
                    requestPath(tpl), serviceToken != null && !serviceToken.isBlank());
        }
    }

    /**
     * 服务 Token 提供者：返回当前服务自身的 JWT（服务身份）。
     * 实现通常从 auth-service 用 API Key 换发，缓存到本地，过期前续签。
     */
    @FunctionalInterface
    public interface ServiceTokenProvider {
        String get();
    }
}
