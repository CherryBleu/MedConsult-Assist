package com.medconsult.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 业务服务侧身份解析过滤器的 bean 注册（独立配置类，避免 gateway 类加载失败）。
 *
 * <p>本配置类用 {@code @ConditionalOnClass(OncePerRequestFilter.class)} 守门：
 * <ul>
 *   <li>servlet 服务（auth/patient/...）：classpath 有 spring-web（OncePerRequestFilter）→ 加载本类 → 注册 filter</li>
 *   <li>gateway（reactive）：classpath 无 spring-web（排除了 common-web）→ 跳过本类 →
 *       不加载 {@link JwtAuthServletFilter} 类，避免 ClassNotFoundException</li>
 * </ul>
 *
 * <p><b>关键</b>：不能把 {@code @Bean JwtAuthServletFilter} 直接写在
 * {@link MedConsultSecurityAutoConfiguration} 里——即使加 @ConditionalOnWebApplication，
 * Spring 在解析返回类型 {@code FilterRegistrationBean<JwtAuthServletFilter>} 时也会触发
 * JwtAuthServletFilter 类加载，进而加载 OncePerRequestFilter，gateway classpath 没这个类就崩。
 * 拆成独立配置类 + @ConditionalOnClass 才能让整个类（含其引用的所有 filter 类）在 gateway 被完全跳过。
 */
@Configuration
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthServletFilterConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtAuthServletFilter.class)
    public FilterRegistrationBean<JwtAuthServletFilter> jwtAuthServletFilter(JwtCodec jwtCodec) {
        JwtAuthServletFilter filter = new JwtAuthServletFilter(jwtCodec);
        FilterRegistrationBean<JwtAuthServletFilter> reg = new FilterRegistrationBean<>(filter);
        // 优先级：在 Spring Security chain（如有）之后、业务 Controller 之前。
        reg.setOrder(Integer.MIN_VALUE + 50);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
