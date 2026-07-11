package com.medconsult.ai.mq;

import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.common.mq.MqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 影像检测异步任务消费者（架构文档 §4.2）。
 *
 * <p>监听 {@code medconsult.ai.image.detect} 队列（由 common-mq 自动声明），
 * 收到任务后异步执行影像检测（调外部 vision 模型）。
 */
@Component
public class ImageDetectionConsumer {
    private static final Logger log = LoggerFactory.getLogger(ImageDetectionConsumer.class);

    private final ImagingDetectionService imagingDetectionService;

    public ImageDetectionConsumer(ImagingDetectionService imagingDetectionService) {
        this.imagingDetectionService = imagingDetectionService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_AI_IMAGE_DETECT)
    public void consume(ImageDetectionTaskMessage message) {
        // traceId：消息带则用消息的，否则本地生成（替代 ai-stack RequestContext.traceId()）
        MDC.put("traceId", message.traceId() == null ? "trace-" + UUID.randomUUID().toString().replace("-", "") : message.traceId());
        try {
            imagingDetectionService.processImageDetection(message.detectionId());
        } catch (RuntimeException ex) {
            log.warn("image detection task failed and will not be requeued, detectionId={}", message.detectionId(), ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
