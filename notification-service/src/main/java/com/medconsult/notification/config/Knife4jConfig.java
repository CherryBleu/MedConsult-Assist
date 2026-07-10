package com.medconsult.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（OpenAPI3）接口文档配置（对齐《接口文档》§2.8 通知 + §4.1 审计日志）。
 *
 * <p>{@code pathsToMatch("/api/v1/**")} 只暴露对外接口；
 * {@code pathsToExclude} 显式排除 {@code /internal/**}（Feign/MQ 内部接口）与 {@code /health/**}（冒烟接口）。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/internal/**", "/health/**")
                .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("通知 + 审计服务 API")
                        .description("站内通知 CRUD / 标记已读 + 审计日志查询"
                                + "（对齐《接口文档》§2.8 + §4.1）")
                        .version("v1"));
    }
}
