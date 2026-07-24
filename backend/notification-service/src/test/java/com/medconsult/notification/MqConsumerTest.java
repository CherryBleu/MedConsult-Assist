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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
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
        "medconsult.notification.mq.notification-queue=medconsult.test.notification.send",
        "medconsult.notification.mq.audit-log-queue=medconsult.test.audit.log",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
@Import(MqConsumerTest.TestRabbitConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MqConsumerTest {

    private static final String TEST_NOTIFICATION_QUEUE = "medconsult.test.notification.send";
    private static final String TEST_AUDIT_LOG_QUEUE = "medconsult.test.audit.log";
    private static final String TEST_NOTIFICATION_EXCHANGE = "medconsult.test.notification";
    private static final String TEST_LOG_EXCHANGE = "medconsult.test.log";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    @BeforeEach
    void purgeTestQueues() {
        rabbitAdmin.purgeQueue(TEST_NOTIFICATION_QUEUE, true);
        rabbitAdmin.purgeQueue(TEST_AUDIT_LOG_QUEUE, true);
    }

    // ===== NotificationConsumer 测试 =====

    @Test
    void notificationConsumer_consumesMessage_andWritesToDb() throws Exception {
        String messageNo = "test-notif-" + UUID.randomUUID();
        clearIdempotentKey(messageNo);
        NotificationEvent event = new NotificationEvent();
        event.setReceiverId("P_TEST_MQ");
        event.setReceiverRole("PATIENT");
        event.setType("SYSTEM");
        event.setTitle("MQ 消费测试通知");
        event.setContent("由 RabbitMQ 投递");

        rabbitTemplate.send(TEST_NOTIFICATION_EXCHANGE, MqConstants.RK_NOTIFICATION_SEND, jsonMessage(event, messageNo));

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
        clearIdempotentKey(messageNo);
        NotificationEvent event = new NotificationEvent();
        event.setReceiverId("P_TEST_DUP");
        event.setReceiverRole("PATIENT");
        event.setType("SYSTEM");
        event.setTitle("幂等测试");
        event.setContent("重复消息只应消费一次");

        // 发两次相同 messageNo 的消息
        for (int i = 0; i < 2; i++) {
            rabbitTemplate.send(TEST_NOTIFICATION_EXCHANGE, MqConstants.RK_NOTIFICATION_SEND, jsonMessage(event, messageNo));
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
        clearIdempotentKey(messageNo);
        AuditLogEvent event = new AuditLogEvent();
        event.setResourceType("PATIENT");
        event.setResourceId("P_TEST_AUDIT");
        event.setAction("VIEW");
        event.setOperatorId("U_MQ_TEST");
        event.setResult("SUCCESS");

        rabbitTemplate.send(TEST_LOG_EXCHANGE, MqConstants.RK_AUDIT_LOG, jsonMessage(event, messageNo));

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
        clearIdempotentKey(messageNo);
        AuditLogEvent event = new AuditLogEvent();
        event.setResourceType("PATIENT");
        event.setResourceId("P_TEST_AUDIT_DUP");
        event.setAction("CREATE");
        event.setOperatorId("U_MQ_DUP");
        event.setResult("SUCCESS");

        // 发两次相同 messageNo
        for (int i = 0; i < 2; i++) {
            rabbitTemplate.send(TEST_LOG_EXCHANGE, MqConstants.RK_AUDIT_LOG, jsonMessage(event, messageNo));
        }

        await().atMost(POLL_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            Long count = auditLogMapper.selectCount(
                    new QueryWrapper<AuditLog>().eq("resource_id", "P_TEST_AUDIT_DUP"));
            org.junit.jupiter.api.Assertions.assertEquals(1L, count,
                    "重复审计消息（同 messageNo）应被幂等去重，只写一条 audit_log");
        });
    }

    private void clearIdempotentKey(String messageNo) {
        redisTemplate.delete(MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo);
    }

    private Message jsonMessage(Object event, String messageNo) throws Exception {
        byte[] body = objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setHeader("messageNo", messageNo)
                .build();
    }

    @TestConfiguration
    static class TestRabbitConfig {
        @Bean
        TopicExchange testNotificationExchange() {
            return new TopicExchange(TEST_NOTIFICATION_EXCHANGE, false, true);
        }

        @Bean
        TopicExchange testLogExchange() {
            return new TopicExchange(TEST_LOG_EXCHANGE, false, true);
        }

        @Bean
        Queue testNotificationQueue() {
            return new Queue(TEST_NOTIFICATION_QUEUE, false, false, true);
        }

        @Bean
        Queue testAuditLogQueue() {
            return new Queue(TEST_AUDIT_LOG_QUEUE, false, false, true);
        }

        @Bean
        Binding bindTestNotificationQueue(Queue testNotificationQueue, TopicExchange testNotificationExchange) {
            return BindingBuilder.bind(testNotificationQueue)
                    .to(testNotificationExchange)
                    .with(MqConstants.RK_NOTIFICATION_SEND);
        }

        @Bean
        Binding bindTestAuditLogQueue(Queue testAuditLogQueue, TopicExchange testLogExchange) {
            return BindingBuilder.bind(testAuditLogQueue)
                    .to(testLogExchange)
                    .with(MqConstants.RK_AUDIT_LOG);
        }
    }
}
