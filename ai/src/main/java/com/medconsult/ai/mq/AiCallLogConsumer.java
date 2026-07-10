package com.medconsult.ai.mq;

import com.medconsult.ai.service.AiCallLogService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiCallLogConsumer {
    private final AiCallLogService callLogService;

    public AiCallLogConsumer(AiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @RabbitListener(queues = "#{@callLogQueue.name}")
    public void consume(AiCallLogMessage message) {
        callLogService.saveFromMessage(message);
    }
}
