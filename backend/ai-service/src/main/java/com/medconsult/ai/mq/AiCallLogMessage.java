package com.medconsult.ai.mq;

import com.medconsult.ai.util.BusinessIds;

import java.math.BigDecimal;

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
        String requestId,
        boolean cacheHit,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal estimatedCostYuan
) {
    public AiCallLogMessage(String type, String patientId, String relatedId, String model, String requestSummary,
                            String responseSummary, String riskLevel, String status, long latencyMs,
                            String errorMessage, String callerService, String traceId, String triggerUserId) {
        this(type, patientId, relatedId, model, requestSummary, responseSummary, riskLevel, status, latencyMs,
                errorMessage, callerService, traceId, triggerUserId, BusinessIds.next("REQ"));
    }

    public AiCallLogMessage(String type, String patientId, String relatedId, String model, String requestSummary,
                            String responseSummary, String riskLevel, String status, long latencyMs,
                            String errorMessage, String callerService, String traceId, String triggerUserId,
                            String requestId) {
        this(type, patientId, relatedId, model, requestSummary, responseSummary, riskLevel, status, latencyMs,
                errorMessage, callerService, traceId, triggerUserId, requestId, false, 0, 0, 0, BigDecimal.ZERO);
    }

    public AiCallLogMessage {
        if (estimatedCostYuan == null) {
            estimatedCostYuan = BigDecimal.ZERO;
        }
    }
}
