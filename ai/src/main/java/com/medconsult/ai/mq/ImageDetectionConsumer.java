package com.medconsult.ai.mq;

import com.medconsult.common.web.RequestContext;
import com.medconsult.ai.service.ImagingDetectionService;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ImageDetectionConsumer {
    private static final Logger log = LoggerFactory.getLogger(ImageDetectionConsumer.class);

    private final ImagingDetectionService imagingDetectionService;

    public ImageDetectionConsumer(ImagingDetectionService imagingDetectionService) {
        this.imagingDetectionService = imagingDetectionService;
    }

    @RabbitListener(queues = "#{@imageDetectionQueue.name}")
    public void consume(ImageDetectionTaskMessage message) {
        MDC.put("traceId", message.traceId() == null ? RequestContext.traceId() : message.traceId());
        try {
            imagingDetectionService.processImageDetection(message.detectionId());
        } catch (RuntimeException ex) {
            log.warn("image detection task failed and will not be requeued, detectionId={}", message.detectionId(), ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
