package com.medconsult.ai.mq;

import com.medconsult.ai.config.AiRabbitListenerConfig;
import com.medconsult.ai.service.AiCallLogService;
import com.medconsult.common.mq.MqConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志异步落库消费者（架构文档 §4.2）。
 *
 * <p>监听 {@code medconsult.ai.calllog} 队列（由 common-mq 自动声明，
 * 绑定到 log exchange，路由键 ai.calllog）。AI 调用日志通过 MQ 异步落库，
 * 避免阻塞主流程。
 *
 * <p><b>异常处理</b>：落库异常必须传播给专用 listener factory。容器执行有界重试，
 * 耗尽后将原消息重发到 AI 死信队列，避免静默丢失或无限重入阻塞。
 */
@Component
public class AiCallLogConsumer {
    private final AiCallLogService callLogService;

    public AiCallLogConsumer(AiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @RabbitListener(
            queues = MqConstants.QUEUE_AI_CALL_LOG,
            containerFactory = AiRabbitListenerConfig.CONTAINER_FACTORY_BEAN_NAME)
    public void consume(AiCallLogMessage message) {
        callLogService.saveFromMessage(message);
    }
}
