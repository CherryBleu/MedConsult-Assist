package com.medconsult.common.feign;

import feign.Request;
import feign.codec.ErrorDecoder;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * common-feign 自动装配（架构文档 §2.4 / §3.2）。
 *
 * <p>业务服务引入本模块后：
 * <ul>
 *   <li>{@link AuthRelayInterceptor} 注册为全局 Feign 拦截器（透传身份/traceId）</li>
 *   <li>{@link FeignErrorDecoder} 作为 Feign 错误解码器（下游错误→BusinessException）</li>
 *   <li>{@link RequestContextRelayFilter}（servlet 环境）接线请求级 token/traceId 到
 *       {@link RequestContext}，并在请求结束清理 ThreadLocal 防泄漏</li>
 *   <li>默认 ServiceTokenProvider 返回 null（无服务 token），业务服务自行覆盖</li>
 *   <li>默认 {@link Request.Options}（connect 5s / read 10s / followRedirects），
 *       替代 Spring Cloud OpenFeign 默认的 10s/60s，避免下游不可用时长时间挂起；
 *       可用 {@code medconsult.feign.connect-timeout/read-timeout} 调整，
 *       优先级低于 OpenFeign 的 properties 与命名 client 配置（详见方法 Javadoc）</li>
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

    /**
     * 默认 Feign 超时配置：connect 5s / read 10s / followRedirects=true。
     *
     * <p>替代 Spring Cloud OpenFeign 默认值（connect 10s / read 60s）——内部服务调用读超时
     * 由 60s 降到 10s，下游不可用或慢响应时调用方快速失败，避免线程长时间挂起。
     *
     * <p>本 bean 是「兜底默认值」——Spring Cloud OpenFeign 默认 {@code defaultToProperties=true}，
     * 因此 properties 配置会作用于 Feign builder 并覆盖 Request.Options bean。覆盖方式
     * （优先级从高到低）：
     * <ol>
     *   <li>{@code spring.cloud.openfeign.client.config.<contextId>.connect-timeout/read-timeout}
     *       （命名级 properties，针对单个 named client，最高）</li>
     *   <li>{@code spring.cloud.openfeign.client.config.default.connect-timeout/read-timeout}
     *       （默认级 properties，作用于 Feign builder，覆盖 Request.Options bean）</li>
     *   <li>业务服务声明自己的 {@link Request.Options} bean（本 bean 带
     *       {@code @ConditionalOnMissingBean} 会自动让位；正常 defaultToProperties=true 下，
     *       业务 bean 仍会被 default properties 覆盖，需设
     *       {@code spring.cloud.openfeign.default-to-properties: false} 才让 bean 真正生效）</li>
     *   <li>{@code medconsult.feign.connect-timeout/read-timeout}（本 bean 的 @Value 兜底默认值，
     *       Duration 格式如 {@code 5s} / {@code 500ms}，最低）</li>
     * </ol>
     *
     * <p>结论：要让本模块的默认值「真正」生效，运维什么都不用配；要让单个服务加严，
     * 改 {@code spring.cloud.openfeign.client.config.default.*} properties 即可，
     * 无需改代码、无需声明 bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignRequestOptions(
            @Value("${medconsult.feign.connect-timeout:5s}") Duration connectTimeout,
            @Value("${medconsult.feign.read-timeout:10s}") Duration readTimeout) {
        return new Request.Options(connectTimeout, readTimeout, true);  // followRedirects=true
    }

    /**
     * 请求级上下文接线 filter：把当前请求的原始 token 写入 RequestContext ThreadLocal，
     * 供 AuthRelayInterceptor 透传；请求结束 finally 清理防泄漏。
     * <p>只在 servlet（非 reactive）web 环境注册——reactive gateway 不走 servlet filter。
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    public RequestContextRelayFilter requestContextRelayFilter(
            @Value("${spring.application.name:unknown}") String applicationName) {
        // callerService 是服务级常量（spring.application.name），启动时设一次，
        // AuthRelayInterceptor 每次 Feign 调用读取它注入 X-Caller-Service 头。
        RequestContext.setCallerService(applicationName);
        return new RequestContextRelayFilter();
    }
}
