package com.medconsult.ai.security;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.common.redis.RateLimiter;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * AI 接口限流过滤器（架构文档 §7.1）。
 *
 * <p>基于 common-redis 的 {@link RateLimiter}（Redis zset 滑动窗口，多实例共享计数），
 * 按 userId / callerService / IP 维度限流，防止 AI 接口成本失控。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AiRateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter rateLimiter;
    private final AiProperties properties;

    public AiRateLimitFilter(RateLimiter rateLimiter, AiProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        AiProperties.RateLimitProperties rateLimit = properties.rateLimit();
        if (rateLimit == null || !rateLimit.enabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/ai/")) {
            return false;
        }
        return !rateLimit.includeInternal() || !uri.startsWith("/internal/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        int windowSeconds = windowSeconds();
        int maxRequests = maxRequests(request);
        String key = redisKey(request);
        if (!rateLimiter.acquire(key, maxRequests, Duration.ofSeconds(windowSeconds))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.getWriter().write("{\"code\":429001,\"message\":\"AI request rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int maxRequests(HttpServletRequest request) {
        AiProperties.RateLimitProperties rateLimit = properties.rateLimit();
        Map<String, Integer> endpointLimits = rateLimit.endpointMaxRequests();
        String endpointKey = request.getMethod() + " " + request.getRequestURI();
        if (endpointLimits != null && endpointLimits.get(endpointKey) != null && endpointLimits.get(endpointKey) > 0) {
            return endpointLimits.get(endpointKey);
        }
        return rateLimit.maxRequests() <= 0 ? 60 : rateLimit.maxRequests();
    }

    private int windowSeconds() {
        AiProperties.RateLimitProperties rateLimit = properties.rateLimit();
        return rateLimit.windowSeconds() <= 0 ? 60 : rateLimit.windowSeconds();
    }

    /**
     * 限流 key 来源（安全考虑，优先从已解析的身份取，不信任请求头）：
     * 1) 当前请求身份（SecurityContext / request attribute，由 JwtAuthServletFilter 写入）——防伪造头绕过限流；
     * 2) 兜底用客户端 IP（匿名或直连场景）。
     * 注：不再直接读 X-User-Id / X-Caller-Service 头，否则直连 ai-service 端口伪造这些头即可每次换身份绕过。
     */
    private String redisKey(HttpServletRequest request) {
        String identity = resolveIdentity(request);
        String prefix = properties.redis() == null ? "medical:" : properties.redis().keyPrefix();
        return prefix + "ratelimit:ai:" + identity + ":" + request.getMethod() + ":" + request.getRequestURI();
    }

    private static String resolveIdentity(HttpServletRequest request) {
        // filter 在 dispatcher servlet 之前执行，RequestContextHolder 可能未初始化，
        // 直接读 JwtAuthServletFilter 写入的 request attribute（与 SecurityContext 同一 key）。
        Object payload = request.getAttribute(SecurityContext.PAYLOAD_ATTR_KEY);
        if (payload instanceof JwtPayload jp) {
            if (jp.isUser() && jp.userId() != null) {
                return "u:" + jp.userId();
            }
            if (jp.isService() && jp.serviceCode() != null) {
                return "s:" + jp.serviceCode();
            }
        }
        // 兜底：匿名 / 直连 / 身份解析失败 → 用 IP
        return "ip:" + request.getRemoteAddr();
    }
}
