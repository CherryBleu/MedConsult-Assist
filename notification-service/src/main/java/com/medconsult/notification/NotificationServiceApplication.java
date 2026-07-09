package com.medconsult.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 通知 + 审计服务启动入口（架构文档 §1.1 notification-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos。
 * <p>{@link MapperScan} 扫描 notification 业务包 + common-mq 包（后者让 LocalMessageMapper 注册，
 *    因 MedConsultMqAutoConfiguration 注入 MessageDispatcher 依赖它）。
 * <p>{@code scanBasePackages} 让 common-web 的 GlobalExceptionHandler / TraceIdFilter 等被扫描。
 *
 * <p>核心职责（架构文档 §2.3 / §7.5 / 《接口文档》§2.8 + §4.1 / 《修改建议》§2.2）：
 * <ul>
 *   <li>站内通知：创建 / 列表 / 标记已读（对外 3 接口）</li>
 *   <li>审计日志：分页查询（对外 1 接口，GET /audit-logs）</li>
 *   <li>内部接口：POST /internal/notifications + /internal/audit-logs（同步写入兜底）</li>
 *   <li>MQ 消费者：消费 notification.send / audit.log 队列写库（项目首个业务 MQ 消费者）</li>
 * </ul>
 *
 * <p>本批 <b>不</b>包含：login_log（归 auth-service）、改造其他服务发 MQ、@AuditLog AOP（留后续）。
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.notification", "com.medconsult.common.web"})
@EnableDiscoveryClient
@MapperScan({"com.medconsult.notification.**.mapper", "com.medconsult.common.mq"})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
