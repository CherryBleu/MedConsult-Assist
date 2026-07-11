package com.medconsult.ai;

import com.medconsult.ai.config.AiProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 辅助问诊服务启动入口（架构文档 §1.1 ai-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link EnableFeignClients} 扫描 common-feign 的客户端（PatientFeignClient / DrugFeignClient /
 *     MedicalRecordFeignClient / AuthFeignClient），由 AuthRelayInterceptor 透传服务身份。
 * <p>{@link MapperScan} 扫描 ai 包下所有 Mapper。
 * <p>{@code scanBasePackages = {"com.medconsult.ai", "com.medconsult.common.web"}} 让 common-web 的
 * GlobalExceptionHandler / TraceIdFilter / ResultBodyAdvice 等 @Component 被扫描。
 *
 * <p>common-redis / common-feign / common-mq / common-security / common-mybatis 的 bean
 * 通过各自的 @AutoConfiguration 自动装配，无需在此显式 scan。
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.ai", "com.medconsult.common.web"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.medconsult.common.feign.client"})
@MapperScan({"com.medconsult.ai.persistence.mapper", "com.medconsult.common.mq"})
@EnableConfigurationProperties(AiProperties.class)
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
