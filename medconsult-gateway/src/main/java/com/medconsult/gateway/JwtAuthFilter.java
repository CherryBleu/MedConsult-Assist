package com.medconsult.gateway;

import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关 JWT 鉴权前置过滤器（架构文档 §4.4 信任边界）。
 *
 * <p>对 /api/v1/* 请求：
 * <ul>
 *   <li>取 Authorization: Bearer &lt;token&gt;，用 JwtCodec 解析</li>
 *   <li>成功：剥离原 Authorization 头，注入 X-User-Id / X-User-Roles / X-Trace-Id</li>
 *   <li>失败：返回 401（未登录/Token 失效）</li>
 *   <li>白名单（/api/v1/auth/login, /register, /refresh, /actuator/**）：放行不鉴权</li>
 * </ul>
 *
 * <p>对 /internal/*：架构文档 §2.1 要求永不对外；此处靠部署网络隔离保证，
 * 网关层也显式拒绝（双保险）。
 *
 * <p>被调业务服务信任 X-User-* 头（§4.4 假设：网络隔离保证外部不可直连业务端口）。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 鉴权白名单（登录/注册/刷新/健康检查不需 token） */
    private static final java.util.Set<String> WHITELIST = java.util.Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/actuator/health");

    @Autowired
    private JwtCodec jwtCodec;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. /internal/* 永不对外（§2.1 双保险）
        if (path.startsWith("/internal/")) {
            log.warn("拒绝外部访问内部接口: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // 2. 生成/透传 traceId
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        ServerHttpRequest.Builder reqBuilder = request.mutate()
                .header("X-Trace-Id", traceId);

        // 3. 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange.mutate().request(reqBuilder.build()).build());
        }

        // 4. 解析 Bearer token
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return rejectUnauthorized(exchange, "缺少 Authorization 头");
        }
        String token = auth.substring(7);
        JwtPayload payload;
        try {
            payload = jwtCodec.parse(token);
        } catch (Exception e) {
            return rejectUnauthorized(exchange, "Token 无效: " + e.getMessage());
        }

        // 5. 注入身份头（剥离原 Authorization，防止业务侧误用原始 token）
        if (payload.isUser()) {
            reqBuilder.header("X-User-Id", String.valueOf(payload.userId()));
            if (payload.roles() != null && !payload.roles().isEmpty()) {
                reqBuilder.header("X-User-Roles", String.join(",", payload.roles()));
            }
            if (payload.primaryRole() != null) {
                reqBuilder.header("X-User-Primary-Role", payload.primaryRole());
            }
        }
        // 移除原 Authorization（业务服务按 X-User-* 头信任）
        reqBuilder.headers(h -> h.remove("Authorization"));

        return chain.filter(exchange.mutate().request(reqBuilder.build()).build());
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::equals)
                || path.startsWith("/actuator/");
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange, String reason) {
        log.debug("鉴权拒绝: {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // 最高优先级（在路由前鉴权）
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
