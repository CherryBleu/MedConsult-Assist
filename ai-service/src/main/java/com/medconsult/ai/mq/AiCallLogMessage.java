package com.medconsult.ai.mq;

public record AiCallLogMessage(
        String type,
        String patientId,
        String relatedId,
        String model,
        String requestSummary,
        String responseSummary,
        String riskLevel,
        String status,
        long latencyMs,
        String errorMessage,
        String callerService,
        String traceId,
        String triggerUserId
) {
}
