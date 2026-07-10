package com.medconsult.drug.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（OpenAPI3）接口文档配置（对齐《接口文档》§2.7 药品库存）。
 *
 * <p>{@code pathsToMatch("/api/v1/**")} 只暴露对外接口，自动排除
 * {@code /internal/**}（Feign 内部接口）。
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
                        .title("药品库存服务 API")
                        .description("药品 / 入库 / 出库 / 流水 / 预警（对齐《接口文档》§2.7）")
                        .version("v1"));
    }
}
