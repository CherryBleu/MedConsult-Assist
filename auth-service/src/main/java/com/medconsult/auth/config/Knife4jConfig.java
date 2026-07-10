package com.medconsult.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（OpenAPI3）接口文档配置（对齐《接口文档》§2.1 用户认证）。
 *
 * <p>{@code pathsToMatch("/api/v1/**")} 只暴露对外接口，自动排除
 * {@code /internal/**}（Feign 内部接口）与 {@code /health/ping}（冒烟接口）。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("用户认证服务 API")
                        .description("注册 / 登录 / 刷新 / 登出 / 当前用户（对齐《接口文档》§2.1）")
                        .version("v1"));
    }
}
