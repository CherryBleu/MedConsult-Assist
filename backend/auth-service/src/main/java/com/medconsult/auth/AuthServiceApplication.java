package com.medconsult.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动入口（架构文档 §1.1 auth-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link MapperScan} 扫描 auth 包下的 Mapper。
 * <p>{@link EnableFeignClients} 启用 Feign，注册 PATIENT 角色时调 patient-service 建档案。
 * <p>{@code scanBasePackages} 让 common-web / common-feign 的 @Component 被扫描
 * （GlobalExceptionHandler / AuthRelayInterceptor / RequestContextRelayFilter 等）。
 *
 * <p>本服务的核心职责（架构文档 §4 / 《修改建议》§2.2 §2.3）：
 * <ul>
 *   <li>用户注册、登录、Token 签发（用户身份 + 服务身份双模）</li>
 *   <li>RBAC 五表管理（sys_role / sys_permission / sys_role_permission / sys_user_role / sys_service_account）</li>
 *   <li>登录日志落库（login_log）</li>
 *   <li>内部接口 /internal/auth/verify / /internal/auth/service-verify 供其他服务校验 Token</li>
 *   <li>注册即建档：PATIENT 角色注册时调 patient-service 自动建档案（§4.2 服务链路）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.auth", "com.medconsult.common.web", "com.medconsult.common.feign"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.medconsult.common.feign.client"})
@MapperScan("com.medconsult.auth.**.mapper")
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
