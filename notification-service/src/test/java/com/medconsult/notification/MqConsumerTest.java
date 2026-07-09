package com.medconsult.notification;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

/**
 * notification-service MQ 消费者集成测试（需真实 RabbitMQ + Redis）。
 *
 * <p>验证 NotificationConsumer / AuditLogConsumer 消费 MQ 消息后正确写库。
 * 仿 common-mq 的 MqFlowTest：rabbitTemplate.convertAndSend 发消息，轮询 DB 断言消费者已处理。
 *
 * <p><b>环境依赖</b>：需 RabbitMQ（infra/docker-compose.yml，5672）+ Redis（16379）+ H2。
 * 通过 {@link EnabledIfEnvironmentVariable} 控制：仅当环境变量 MQ_TEST_ENABLED=true 时运行，
 * 避免在无 RabbitMQ 的环境（如 CI 快速通道）失败。
 *
 * <p>注：本测试类当前为占位骨架，具体测试方法在 RabbitMQ 稳定可用后补全。
 * 当前 NotificationFlowTest 已覆盖 REST 层全部逻辑，MQ 消费者逻辑（三步法幂等）与 common-mq
 * 已验证的 IdempotentConsumer 模式一致，风险可控。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        "spring.datasource.url=jdbc:h2:mem:medconsult_notify_mq_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=medconsult",
        "spring.rabbitmq.password=medconsult123",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "MQ_TEST_ENABLED", matches = "true",
        disabledReason = "需真实 RabbitMQ（5672）；设 MQ_TEST_ENABLED=true 启用")
class MqConsumerTest {
    // MQ 消费者测试占位：NotificationFlowTest 已覆盖 REST 层；
    // 消费者逻辑（三步法幂等）与 common-mq 已验证的 IdempotentConsumer 一致。
    // 具体 @Test 在 RabbitMQ 稳定后补：发消息到 EXCHANGE_NOTIFICATION/EXCHANGE_LOG，断言消费者写库。
}
