package com.medconsult.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.common.mq.audit.AuditLogEvent;
import com.medconsult.notification.audit.entity.AuditLog;
import com.medconsult.notification.audit.mapper.AuditLogMapper;
import com.medconsult.notification.consumer.NotificationEvent;
import com.medconsult.notification.notification.entity.Notification;
import com.medconsult.notification.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * notification-service MQ 消费者集成测试（需真实 RabbitMQ + Redis）。
 *
 * <p>验证 {@link com.medconsult.notification.consumer.NotificationConsumer} /
 * {@link com.medconsult.notification.consumer.AuditLogConsumer} 消费 MQ 消息后正确写库，
 * 以及原子 SETNX 幂等（重复消息不产生重复记录）。
 *
 * <p><b>环境依赖</b>：RabbitMQ（infra/docker-compose.yml:5672）+ Redis（16379）+ H2。
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
        // 消费者自动启动（本测试就是要验证消费者消费消息）
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MqConsumerTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private DataSource dataSource;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    // ===== NotificationConsumer 测试 =====

    @Test
    void notificationConsumer_consumesMessage_andWritesToDb() throws Exception {
        String messageNo = "test-notif-" + UUID.randomUUID();
        NotificationEvent event = new NotificationEvent();
        event.setReceiverId("P_TEST_MQ");
        event.setReceiverRole("PATIENT");
        event.setType("SYSTEM");
        event.setTitle("MQ 消费测试通知");
        event.setContent("由 RabbitMQ 投递");

        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend(
                MqConstants.EXCHANGE_NOTIFICATION,
                MqConstants.RK_NOTIFICATION_SEND,
                payload,
                m -> {
                    m.getMessageProperties().getHeaders().put("messageNo", messageNo);
                    return m;
                });

        // 轮询等待消费者处理
        await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            Long count = notificationMapper.selectCount(
                    new QueryWrapper<Notification>().eq("receiver_id", "P_TEST_MQ"));
            org.junit.jupiter.api.Assertions.assertTrue(count != null && count > 0,
                    "消费者应消费通知消息并写入 notification 表");
        });
    }

    @Test
    void notificationConsumer_duplicateMessage_idempotent() throws Exception {
        String messageNo = "test-notif-dup-" + UUID.randomUUID();
        NotificationEvent event = new NotificationEvent();
        event.setReceiverId("P_TEST_DUP");
        event.setReceiverRole("PATIENT");
        event.setType("SYSTEM");
        event.setTitle("幂等测试");
        event.setContent("重复消息只应消费一次");

        String payload = objectMapper.writeValueAsString(event);

        // 发两次相同 messageNo 的消息
        for (int i = 0; i < 2; i++) {
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_NOTIFICATION,
                    MqConstants.RK_NOTIFICATION_SEND,
                    payload,
                    m -> {
                        m.getMessageProperties().getHeaders().put("messageNo", messageNo);
                        return m;
                    });
        }

        // 等待消费者处理完
        await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            Long count = notificationMapper.selectCount(
                    new QueryWrapper<Notification>().eq("receiver_id", "P_TEST_DUP"));
            org.junit.jupiter.api.Assertions.assertEquals(1L, count,
                    "重复消息（同 messageNo）应被幂等去重，只写一条 notification");
        });
    }

    // ===== AuditLogConsumer 测试 =====

    @Test
    void auditLogConsumer_consumesMessage_andWritesToDb() throws Exception {
        String messageNo = "test-audit-" + UUID.randomUUID();
        AuditLogEvent event = new AuditLogEvent();
        event.setResourceType("PATIENT");
        event.setResourceId("P_TEST_AUDIT");
        event.setAction("VIEW");
        event.setOperatorId("U_MQ_TEST");
        event.setResult("SUCCESS");

        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend(
                MqConstants.EXCHANGE_LOG,
                MqConstants.RK_AUDIT_LOG,
                payload,
                m -> {
                    m.getMessageProperties().getHeaders().put("messageNo", messageNo);
                    return m;
                });

        await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            Long count = auditLogMapper.selectCount(
                    new QueryWrapper<AuditLog>().eq("resource_id", "P_TEST_AUDIT"));
            org.junit.jupiter.api.Assertions.assertTrue(count != null && count > 0,
                    "消费者应消费审计消息并写入 audit_log 表");
        });
    }

    @Test
    void auditLogConsumer_duplicateMessage_idempotent() throws Exception {
        String messageNo = "test-audit-dup-" + UUID.randomUUID();
        AuditLogEvent event = new AuditLogEvent();
        event.setResourceType("PATIENT");
        event.setResourceId("P_TEST_AUDIT_DUP");
        event.setAction("CREATE");
        event.setOperatorId("U_MQ_DUP");
        event.setResult("SUCCESS");

        String payload = objectMapper.writeValueAsString(event);

        // 发两次相同 messageNo
        for (int i = 0; i < 2; i++) {
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_LOG,
                    MqConstants.RK_AUDIT_LOG,
                    payload,
                    m -> {
                        m.getMessageProperties().getHeaders().put("messageNo", messageNo);
                        return m;
                    });
        }

        await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            Long count = auditLogMapper.selectCount(
                    new QueryWrapper<AuditLog>().eq("resource_id", "P_TEST_AUDIT_DUP"));
            org.junit.jupiter.api.Assertions.assertEquals(1L, count,
                    "重复审计消息（同 messageNo）应被幂等去重，只写一条 audit_log");
        });
    }
}
