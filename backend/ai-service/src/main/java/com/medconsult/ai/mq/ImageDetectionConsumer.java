package com.medconsult.ai.mq;

import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.common.mq.IdempotentConsumer;
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
 *
 * <p>幂等：以 detectionId 作为 messageNo，MQ 重投 / 多实例并发消费时，
 * 靠 {@link IdempotentConsumer}（Redis SETNX）吸收去重，避免重复调外部模型、重复写库、重复计费。
 * （processImageDetection 内部对 COMPLETED 有跳过，但 PROCESSING 状态无保护，故在此再补一道幂等。）
 */
@Component
public class ImageDetectionConsumer {
    private static final Logger log = LoggerFactory.getLogger(ImageDetectionConsumer.class);

    private final ImagingDetectionService imagingDetectionService;
    private final IdempotentConsumer idempotentConsumer;

    public ImageDetectionConsumer(ImagingDetectionService imagingDetectionService, IdempotentConsumer idempotentConsumer) {
        this.imagingDetectionService = imagingDetectionService;
        this.idempotentConsumer = idempotentConsumer;
    }

    @RabbitListener(queues = MqConstants.QUEUE_AI_IMAGE_DETECT)
    public void consume(ImageDetectionTaskMessage message) {
        // traceId：消息带则用消息的，否则本地生成（替代 ai-stack RequestContext.traceId()）
        MDC.put("traceId", message.traceId() == null ? "trace-" + UUID.randomUUID().toString().replace("-", "") : message.traceId());
        try {
            // 幂等：同一 detectionId 只处理一次（Redis SETNX）。
            // 注意：action 抛异常时 IdempotentConsumer 会删除标记"允许重投"，但下方 catch
            // 会吞掉异常让 broker ack——即单次消费失败后不自动重试（避免对外部 vision 模型重复计费）。
            // 检测记录已在 processImageDetection 内部标记为 FAILED，用户可在前端重新提交。
            idempotentConsumer.executeOnce("imaging-detect:" + message.detectionId(), () -> {
                imagingDetectionService.processImageDetection(message.detectionId());
                return null;
            });
        } catch (RuntimeException ex) {
            // 不重投（detection 已标 FAILED，用户可重新提交；自动重试会对外部模型重复计费）
            log.warn("image detection task failed (marked FAILED, not requeued), detectionId={}",
                    message.detectionId(), ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
