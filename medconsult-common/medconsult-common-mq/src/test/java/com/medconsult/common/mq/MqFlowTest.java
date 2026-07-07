package com.medconsult.common.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * common-mq 集成测试：H2 持久化 + RabbitMQ 真实投递 + Redis 幂等。
 *
 * <p>验证点（架构文档 §6 / §4.2）：
 * <ul>
 *   <li>LocalMessage 持久化（insert 后可查回）</li>
 *   <li>IdempotentConsumer：同 messageNo 第二次执行被跳过</li>
 *   <li>MessageDispatcher：插 PENDING 消息后，调度扫描投递到 RabbitMQ，最终 CONFIRMED</li>
 *   <li>@RabbitListener 真实消费到消息（端到端）</li>
 * </ul>
 */
@SpringBootTest
class MqFlowTest {

    @Autowired
    LocalMessageMapper localMessageMapper;

    @Autowired
    IdempotentConsumer idempotent;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    TestConsumer testConsumer;

    @Test
    void localMessage_persistsAndReloads() {
        LocalMessage msg = LocalMessage.of(
                MqConstants.EXCHANGE_NOTIFICATION, "msg-persist-" + System.nanoTime(),
                MqConstants.RK_NOTIFICATION_SEND, "{\"k\":\"v\"}");
        localMessageMapper.insert(msg);
        assertNotNull(msg.getId());

        LocalMessage reloaded = localMessageMapper.selectById(msg.getId());
        assertNotNull(reloaded);
        assertEquals(LocalMessage.STATUS_PENDING, reloaded.getStatus());
        assertEquals(0, reloaded.getRetryCount());
        assertEquals("{\"k\":\"v\"}", reloaded.getPayloadJson());
    }

    @Test
    void idempotentConsumer_executesOnceAndSkipsDuplicates() {
        String no = "idem-" + System.nanoTime();
        AtomicInteger counter = new AtomicInteger(0);

        Integer r1 = idempotent.executeOnce(no, () -> counter.incrementAndGet());
        Integer r2 = idempotent.executeOnce(no, () -> counter.incrementAndGet());

        assertEquals(1, r1, "首次执行应返回结果");
        assertNull(r2, "重复 messageNo 应返回 null（跳过）");
        assertEquals(1, counter.get(), "业务逻辑应只执行一次");
        assertTrue(idempotent.isAlreadyProcessed(no));
    }

    @Test
    void dispatcher_publishesAndConfirms() throws InterruptedException {
        // 插一条 PENDING，等 MessageDispatcher 扫描投递（@Scheduled 每 5s 一次）
        String msgNo = "dispatch-" + System.nanoTime();
        LocalMessage msg = LocalMessage.of(
                MqConstants.EXCHANGE_NOTIFICATION, msgNo,
                MqConstants.RK_NOTIFICATION_SEND, "{\"event\":\"test\"}");
        localMessageMapper.insert(msg);

        // 等待调度器扫描（最长 8s，给 5s 周期 + 投递余量）
        boolean confirmed = false;
        for (int i = 0; i < 40; i++) {
            LocalMessage cur = localMessageMapper.selectById(msg.getId());
            if (cur != null && LocalMessage.STATUS_CONFIRMED.equals(cur.getStatus())) {
                confirmed = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(confirmed, "Dispatcher 应在 8s 内把 PENDING 投递并置 CONFIRMED");
    }

    @Test
    void endToEnd_rabbitListenerReceivesMessage() throws InterruptedException {
        // 直接用 RabbitTemplate 发，验证 TestConsumer 收到（不走 local_message）
        rabbitTemplate.convertAndSend(
                MqConstants.EXCHANGE_NOTIFICATION,
                MqConstants.RK_NOTIFICATION_SEND,
                "e2e-" + System.nanoTime());

        assertTrue(testConsumer.latch.await(5, TimeUnit.SECONDS),
                "@RabbitListener 应在 5s 内收到消息");
    }

    /** 测试消费者 TestConsumer 已提取为顶层类（避免嵌套 @Component 不被 scan）。 */
}
