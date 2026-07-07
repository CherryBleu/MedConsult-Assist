package com.medconsult.common.feign;

import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * common-feign 自动装配（架构文档 §2.4 / §3.2）。
 *
 * <p>业务服务引入本模块后：
 * <ul>
 *   <li>{@link AuthRelayInterceptor} 注册为全局 Feign 拦截器（透传身份/traceId）</li>
 *   <li>{@link FeignErrorDecoder} 作为 Feign 错误解码器（下游错误→BusinessException）</li>
 *   <li>默认 ServiceTokenProvider/UserTokenProvider 返回 null（无 token 透传），
 *       业务服务自行覆盖（auth-service 换发服务 token / AuthFilter 提供用户 token）</li>
 * </ul>
 *
 * <p>前提：业务服务须 {@code @EnableFeignClients} 启用 Feign（本模块不强制开启）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class MedConsultFeignAutoConfiguration {

    /**
     * 默认服务 Token 提供者：返回 null。
     * 业务服务（需要调 /internal/* 的）必须覆盖此 bean，从 auth-service 换发服务 JWT。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthRelayInterceptor.ServiceTokenProvider defaultServiceTokenProvider() {
        return () -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthRelayInterceptor authRelayInterceptor(AuthRelayInterceptor.ServiceTokenProvider stp) {
        return new AuthRelayInterceptor(stp);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
