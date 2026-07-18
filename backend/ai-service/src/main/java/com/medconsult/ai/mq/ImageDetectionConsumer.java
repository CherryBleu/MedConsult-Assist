package com.medconsult.ai.mq;

import com.medconsult.ai.config.AiRabbitListenerConfig;
import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.common.mq.IdempotentConsumer;
import com.medconsult.common.mq.MqConstants;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 影像检测异步任务消费者（架构文档 §4.2）。
 *
 * <p>监听 {@code medconsult.ai.image.detect} 队列（由 common-mq 自动声明），
 * 收到任务后异步执行影像检测（调外部 vision 模型）。
 *
 * <p>幂等：以 detectionId 作为 messageNo，MQ 重投 / 多实例并发消费时，
 * 靠 {@link IdempotentConsumer}（Redis SETNX）吸收去重，避免重复调外部模型、重复写库、重复计费。
 * （processImageDetection 内部对 COMPLETED 有跳过，但 PROCESSING 状态无保护，故在此再补一道幂等。）
 * 业务失败时检测记录仍由 service 标记为 FAILED；幂等 marker 回滚后异常继续传播，由容器重试。
 */
@Component
public class ImageDetectionConsumer {
    private final ImagingDetectionService imagingDetectionService;
    private final IdempotentConsumer idempotentConsumer;

    public ImageDetectionConsumer(ImagingDetectionService imagingDetectionService, IdempotentConsumer idempotentConsumer) {
        this.imagingDetectionService = imagingDetectionService;
        this.idempotentConsumer = idempotentConsumer;
    }

    @RabbitListener(
            queues = MqConstants.QUEUE_AI_IMAGE_DETECT,
            containerFactory = AiRabbitListenerConfig.CONTAINER_FACTORY_BEAN_NAME)
    public void consume(ImageDetectionTaskMessage message) {
        // traceId：消息带则用消息的，否则本地生成（替代 ai-stack RequestContext.traceId()）
        MDC.put("traceId", message.traceId() == null ? "trace-" + UUID.randomUUID().toString().replace("-", "") : message.traceId());
        try {
            // action 失败时 IdempotentConsumer 删除 marker，容器下一次尝试才能再次执行。
            idempotentConsumer.executeOnce("imaging-detect:" + message.detectionId(), () -> {
                imagingDetectionService.processImageDetection(message.detectionId());
                return null;
            });
        } finally {
            MDC.remove("traceId");
        }
    }
}
