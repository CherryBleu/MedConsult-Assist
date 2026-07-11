package com.medconsult.common.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * MqFlowTest 的测试消费者（顶层类，确保被 component scan）。
 * 监听 notification queue 验证端到端投递。
 */
@Component
public class TestConsumer {
    public final CountDownLatch latch = new CountDownLatch(1);

    @RabbitListener(queues = MqConstants.QUEUE_NOTIFICATION_SEND)
    public void onMessage(String payload) {
        latch.countDown();
    }

    public void reset() {
        // CountDownLatch 不可重置；测试用单次倒计时即可
    }
}
