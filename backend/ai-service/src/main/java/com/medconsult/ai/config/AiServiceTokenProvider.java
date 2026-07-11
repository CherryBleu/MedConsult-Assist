package com.medconsult.ai.config;

import com.medconsult.common.feign.AuthRelayInterceptor;
import com.medconsult.common.feign.client.AuthFeignClient;
import com.medconsult.common.feign.dto.ServiceTokenDTO;
import com.medconsult.common.core.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * ai-service 的服务身份 Token 提供者（架构文档 §4.2 / §2.4）。
 *
 * <p>覆盖 common-feign 默认的 {@link AuthRelayInterceptor.ServiceTokenProvider}（返回 null）。
 * 当 AI 服务以服务身份（非用户链路）调用其他服务的 /internal/** 时，
 * {@link AuthRelayInterceptor} 会调用本提供者获取服务 JWT 注入 Authorization 头，
 * 下游 {@code SecurityContext.requireService()} 校验通过。
 *
 * <p><b>token 获取</b>：用 serviceCode + apiKey 调 auth-service 的
 * /internal/auth/service-token 换发 SERVICE 类型 JWT。
 *
 * <p><b>缓存</b>：token 存 Redis（key=medconsult:ai:service-token），TTL 略短于 token 有效期，
 * 避免每次 Feign 调用都换 token。token 获取失败时返回 null（下游会 401，但不阻断启动）。
 */
@Configuration
public class AiServiceTokenProvider implements AuthRelayInterceptor.ServiceTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(AiServiceTokenProvider.class);
    private static final String CACHE_KEY = "medconsult:ai:service-token";
    /** 缓存 TTL：比 service token 有效期（1h=3600s）略短，留 5 分钟续签窗口 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(3300);

    private final AuthFeignClient authFeignClient;
    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public AiServiceTokenProvider(AuthFeignClient authFeignClient, StringRedisTemplate redis, AiProperties properties) {
        this.authFeignClient = authFeignClient;
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public String get() {
        // 1. 先读缓存
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        } catch (RuntimeException ex) {
            // Redis 不可用时兜底直连 auth-service
        }

        // 2. 缓存未命中：调 auth-service 换发
        AiProperties.InternalProperties internal = properties.internal();
        if (internal == null || internal.serviceCode() == null || internal.apiKey() == null) {
            log.warn("ai-service service-code/api-key not configured, cannot obtain service token");
            return null;
        }
        try {
            Result<ServiceTokenDTO.Response> result = authFeignClient.issueServiceToken(
                    new ServiceTokenDTO.Request(internal.serviceCode(), internal.apiKey()));
            if (result == null || result.data() == null || result.data().accessToken() == null) {
                log.warn("auth-service returned empty service token");
                return null;
            }
            String token = result.data().accessToken();
            // 3. 写缓存
            try {
                redis.opsForValue().set(CACHE_KEY, token, CACHE_TTL);
            } catch (RuntimeException ignored) {
                // 缓存写失败不影响返回 token
            }
            return token;
        } catch (RuntimeException ex) {
            log.warn("failed to obtain service token from auth-service", ex);
            return null;
        }
    }
}
