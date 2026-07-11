package com.medconsult.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动入口（架构文档 §1.1 gateway）。
 *
 * <p>WebFlux 反应式（非 servlet）。职责（架构文档 §4.4）：
 * <ul>
 *   <li>路由 /api/v1/* → 后端服务（基于 Nacos 服务名 lb:// 负载均衡）</li>
 *   <li>鉴权前置：解析 Bearer JWT，写入 X-User-Id/X-User-Roles/X-Trace-Id 头</li>
 *   <li>对 /internal/* 直接 404（永不对外，§2.1）</li>
 *   <li>限流（后续加，Redis 令牌桶）</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
