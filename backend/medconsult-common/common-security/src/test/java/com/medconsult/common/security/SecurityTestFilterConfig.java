package com.medconsult.common.security;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * PermissionAspectTest 的测试 AuthFilter（顶层 @Configuration，确保被扫描）。
 *
 * <p>把 X-Test-User / X-Test-Scope 头转成 {@link JwtPayload} 注入 {@link SecurityContext}，
 * 模拟真实业务服务的 AuthFilter 行为。
 */
@Configuration
public class SecurityTestFilterConfig {

    @Bean
    FilterRegistrationBean<Filter> testAuthFilter() {
        Filter filter = (req, resp, chain) -> {
            if (req instanceof HttpServletRequest http) {
                String role = http.getHeader("X-Test-User");
                String scopeRaw = http.getHeader("X-Test-Scope");
                if (role != null) {
                    List<String> scopes = (scopeRaw == null || scopeRaw.isBlank())
                            ? List.of()
                            : Arrays.stream(scopeRaw.split(",")).map(String::trim)
                                .filter(s -> !s.isEmpty()).toList();
                    JwtPayload p = new JwtPayload(
                            JwtPayload.SubjectType.USER,
                            1L, null, "tester",
                            List.of(role), role, null, null, null, null,
                            scopes, "test-jti", 0L);
                    SecurityContext.setPayload(p);
                }
            }
            chain.doFilter(req, resp);
        };
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/perm/*");
        return reg;
    }
}
