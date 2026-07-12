package com.medconsult.auth.config;

import com.medconsult.common.feign.AuthRelayInterceptor;
import com.medconsult.common.security.JwtCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * auth-service 自身的服务身份 Token 提供者（架构文档 §4.2）。
 *
 * <p><b>与 ai-service 的 {@code AiServiceTokenProvider} 区别</b>：ai-service 是 JWT 的
 * <i>消费方</i>，需用 serviceCode + apiKey 调 auth-service 的 /internal/auth/service-token 换 token；
 * 而 auth-service 本身就是 JWT 的 <i>签发方</i>，持有 {@link JwtCodec}，
 * 直接本地签发自己的服务 JWT 即可，无需 Feign 自调用（避免 auth→auth 循环依赖）。
 *
 * <p>签发的 SERVICE JWT 用于 auth-service 调 patient-service 的
 * /internal/patients/register（注册即建档），下游 SecurityContext.requireService 校验通过。
 *
 * <p><b>缓存</b>：token 存 Redis，TTL 略短于有效期，避免每次 Feign 调用都重签。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthServiceTokenProvider implements AuthRelayInterceptor.ServiceTokenProvider {

    private static final String CACHE_KEY = "medconsult:auth:service-token";
    /** 缓存 TTL：比 token 有效期略短，留续签窗口 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(3300);
    /** token 有效期 1 小时 */
    private static final long TOKEN_TTL = 3600L;

    private final JwtCodec jwtCodec;
    private final StringRedisTemplate redis;

    @Value("${medconsult.auth.service-code:auth-service}")
    private String serviceCode;

    @Override
    public String get() {
        // 1. 先读缓存
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时兜底直签
        }

        // 2. 本地签发 auth-service 自己的服务 JWT（不走 Feign 自调用）
        try {
            String token = jwtCodec.signService(
                    serviceCode, "认证服务", List.of("patient:write"), TOKEN_TTL, null);
            // 3. 写缓存
            try {
                redis.opsForValue().set(CACHE_KEY, token, CACHE_TTL);
            } catch (RuntimeException ignored) {
                // 缓存写失败不影响返回
            }
            return token;
        } catch (RuntimeException ex) {
            log.warn("failed to sign auth-service service token", ex);
            return null;
        }
    }
}
