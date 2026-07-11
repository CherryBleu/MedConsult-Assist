package com.medconsult.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * common-web 自动装配。
 *
 * <p>业务服务引入 {@code medconsult-common-web} 依赖后：
 * <ul>
 *   <li>{@link TraceIdFilter} 注册为 Servlet Filter（最高优先级）</li>
 *   <li>{@link GlobalExceptionHandler} 与 {@link ResultBodyAdvice} 由 {@code @RestControllerAdvice}
 *       自动被 Spring 扫描</li>
 *   <li>{@link MaskingSerializer} 由 Jackson 按 {@code @Mask} 注解触发</li>
 * </ul>
 *
 * <p>仅当 classpath 含 Spring Web 且应用是 Servlet Web 应用时生效（避免在非 Web 模块误装配）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServlet")
public class MedConsultWebAutoConfiguration {

    /**
     * 注册 TraceIdFilter，最高优先级确保在所有过滤器之前执行。
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setName("traceIdFilter");
        return reg;
    }
}
