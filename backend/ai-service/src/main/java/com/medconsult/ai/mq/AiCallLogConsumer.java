package com.medconsult.ai.mq;

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
 */
@Component
public class AiCallLogConsumer {
    private final AiCallLogService callLogService;

    public AiCallLogConsumer(AiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_AI_CALL_LOG)
    public void consume(AiCallLogMessage message) {
        callLogService.saveFromMessage(message);
    }
}
