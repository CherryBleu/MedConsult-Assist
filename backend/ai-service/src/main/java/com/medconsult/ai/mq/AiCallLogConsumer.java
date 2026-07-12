package com.medconsult.ai.mq;

import com.medconsult.ai.service.AiCallLogService;
import com.medconsult.common.mq.MqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志异步落库消费者（架构文档 §4.2）。
 *
 * <p>监听 {@code medconsult.ai.calllog} 队列（由 common-mq 自动声明，
 * 绑定到 log exchange，路由键 ai.calllog）。AI 调用日志通过 MQ 异步落库，
 * 避免阻塞主流程。
 *
 * <p><b>异常处理</b>：调用日志是非关键的审计辅助数据，单条落库失败不应阻塞整个队列。
 * 之前无 try-catch：saveFromMessage 抛异常（如 DB 短暂故障、字段超长）时，
 * RabbitMQ 默认 requeue-rejected=true 会无限重入，毒丸消息头阻塞整个 calllog 队列。
 * 现吞掉异常 + 记 ERROR 日志，让 broker ack 消息，单条日志丢失可接受（不入库不影响主业务）。
 */
@Component
public class AiCallLogConsumer {
    private static final Logger log = LoggerFactory.getLogger(AiCallLogConsumer.class);

    private final AiCallLogService callLogService;

    public AiCallLogConsumer(AiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_AI_CALL_LOG)
    public void consume(AiCallLogMessage message) {
        try {
            callLogService.saveFromMessage(message);
        } catch (RuntimeException ex) {
            // 吞掉异常避免毒丸无限重入阻塞队列；调用日志丢失可接受，仅记 ERROR 便于排查
            log.error("AI 调用日志落库失败（已跳过，不阻塞队列）: type={} relatedId={}",
                    message.type(), message.relatedId(), ex);
        }
    }
}
