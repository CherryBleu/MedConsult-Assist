package com.medconsult.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（OpenAPI3）接口文档配置。
 *
 * <p>{@code pathsToMatch("/api/v1/**")} 只暴露对外接口；
 * {@code pathsToExclude} 显式排除 {@code /internal/**}（服务间内部接口）与 {@code /health/**}。
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
                        .title("AI 辅助问诊服务 API")
                        .description("智能分诊 / 症状对话 / 病历摘要 / 用药分析 / 影像检测 / 反馈 / 调用日志")
                        .version("v1"));
    }
}
