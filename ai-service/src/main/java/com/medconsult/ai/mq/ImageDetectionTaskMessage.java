package com.medconsult.ai.mq;

public record ImageDetectionTaskMessage(
        String detectionId,
        String traceId
) {
}
