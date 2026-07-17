package com.medconsult.ai.mq;

import com.medconsult.ai.util.BusinessIds;

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
        String triggerUserId,
        String requestId
) {
    public AiCallLogMessage(String type, String patientId, String relatedId, String model, String requestSummary,
                            String responseSummary, String riskLevel, String status, long latencyMs,
                            String errorMessage, String callerService, String traceId, String triggerUserId) {
        this(type, patientId, relatedId, model, requestSummary, responseSummary, riskLevel, status, latencyMs,
                errorMessage, callerService, traceId, triggerUserId, BusinessIds.next("REQ"));
    }
}
