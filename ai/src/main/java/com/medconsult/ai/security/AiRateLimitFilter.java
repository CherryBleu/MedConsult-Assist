package com.medconsult.ai.security;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.common.redis.RedisRateLimiter;
import com.medconsult.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AiRateLimitFilter extends OncePerRequestFilter {
    private final RedisRateLimiter rateLimiter;
    private final AiProperties properties;

    public AiRateLimitFilter(RedisRateLimiter rateLimiter, AiProperties properties) {
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

    private String redisKey(HttpServletRequest request) {
        String identity = firstPresent(
                request.getHeader(RequestContext.USER_ID_HEADER),
                request.getHeader(RequestContext.TRIGGER_USER_ID_HEADER),
                request.getHeader(RequestContext.CALLER_SERVICE_HEADER),
                request.getRemoteAddr()
        );
        String prefix = properties.redis() == null ? "medical:" : properties.redis().keyPrefix();
        return prefix + "ratelimit:ai:" + identity + ":" + request.getMethod() + ":" + request.getRequestURI();
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "anonymous";
    }
}
