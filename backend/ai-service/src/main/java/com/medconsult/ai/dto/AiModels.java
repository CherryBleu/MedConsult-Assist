package com.medconsult.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AiModels {
    private AiModels() {
    }

    public record SymptomChatRequest(
            @NotBlank String sessionId,
            String patientId,
            @NotBlank String message,
            PatientContext patientContext,
            RagOptions ragOptions
    ) {
    }

    public record PatientContext(
            Integer age,
            String gender,
            List<String> allergies,
            List<String> pastMedicalHistory,
            List<String> currentMedications
    ) {
    }

    public record RagOptions(
            String knowledgeSource,
            Integer topK,
            Boolean returnCitations
    ) {
        public int safeTopK() {
            return topK == null || topK <= 0 ? 5 : topK;
        }
    }

    public record SymptomChatResponse(
            String sessionId,
            String answer,
            String answerSource,
            List<String> possibleCauses,
            List<String> suggestedDepartments,
            String riskLevel,
            boolean emergencyAdvice,
            List<VectorMatchDto> vectorMatches,
            List<CitationDto> citations
    ) {
    }

    public record VectorMatchDto(
            String vectorId,
            double score,
            String sourceId,
            String diseaseName,
            String fieldName,
            String chunkText
    ) {
    }

    public record CitationDto(
            String sourceId,
            String diseaseName,
            List<String> matchedFields,
            String snippet,
            double score
    ) {
    }

    public record TriageRequest(
            String patientId,
            @NotEmpty List<String> symptoms,
            String duration,
            String severity,
            Integer age,
            String gender,
            List<String> pastMedicalHistory,
            Boolean needAvailableSchedules,
            String preferredDate
    ) {
    }

    public record TriageResponse(
            boolean emergencyRecommended,
            List<TriageRecommendationDto> recommendations
    ) {
    }

    public record TriageRecommendationDto(
            String departmentId,
            String departmentName,
            double confidence,
            int priority,
            String reason,
            List<AvailableScheduleDto> availableSchedules
    ) {
    }

    public record AvailableScheduleDto(
            String scheduleId,
            String doctorName,
            String period,
            Integer remainingQuota
    ) {
    }

    public record MedicalRecordSummaryRequest(
            @NotBlank String recordId,
            String summaryType,
            Boolean saveDraft
    ) {
    }

    public record MedicalRecordSummaryTextRequest(
            @NotBlank String recordText,
            String summaryType
    ) {
    }

    public record MedicalRecordSummaryResponse(
            String summaryId,
            String recordId,
            Map<String, Object> summary,
            String status
    ) {
    }

    public record MedicalRecordSummaryTextResponse(
            Map<String, Object> summary,
            String status
    ) {
    }

    public record SummaryConfirmRequest(
            String summaryId,
            String confirmedBy,
            @NotNull Map<String, Object> confirmedSummary
    ) {
    }

    public record SummaryConfirmResponse(
            String summaryId,
            String recordId,
            String status
    ) {
    }

    public record MedicationAnalysisRequest(
            String patientId,
            String recordId,
            String prescriptionId,
            @Valid @NotEmpty List<PrescriptionDto> prescriptions,
            PatientContext patientContext,
            Boolean returnFunctionTrace
    ) {
    }

    public record PrescriptionDto(
            String drugId,
            @NotBlank String drugName,
            String dosage,
            String frequency,
            String route,
            Integer days
    ) {
    }

    public record MedicationAnalysisResponse(
            String analysisId,
            String overallRiskLevel,
            List<Map<String, Object>> contraindicationRisks,
            List<Map<String, Object>> interactionRisks,
            List<Map<String, Object>> reminders,
            List<Map<String, Object>> functionTrace
    ) {
    }

    public record ReportAnalysisRequest(
            String patientId,
            String recordId,
            String attachmentId,
            @NotBlank String reportType,
            @NotBlank String reportText,
            ExternalModelDto externalModel
    ) {
    }

    public record ReportAnalysisResponse(
            String analysisId,
            String status,
            boolean abnormalDetected,
            List<Map<String, Object>> findings,
            String disclaimer
    ) {
    }

    public record ImageDetectionRequest(
            String patientId,
            String recordId,
            @NotBlank String imageType,
            List<String> fileIds,
            List<String> imageUrls,
            String storageType,
            ExternalModelDto externalModel
    ) {
    }

    public record ImageDetectionResponse(
            String detectionId,
            String status,
            boolean abnormalDetected,
            List<Map<String, Object>> findings,
            String reviewStatus,
            String reviewResult,
            String reviewComment,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            String disclaimer
    ) {
    }

    public record FileUploadResponse(
            String fileId,
            String fileUrl,
            Long fileSize,
            String fileType,
            String storageType,
            String bucket,
            String objectKey,
            String originalFilename
    ) {
    }

    public record ChunkUploadResponse(
            String uploadId,
            int chunkIndex,
            int totalChunks,
            boolean completed,
            FileUploadResponse file
    ) {
    }

    public record ExternalModelDto(
            String provider,
            String model
    ) {
    }

    public record ImagingReviewResponse(
            String detectionId,
            String reviewStatus,
            String reviewResult,
            String reviewComment,
            Long reviewedBy,
            LocalDateTime reviewedAt
    ) {
    }

    public record AiReviewRequest(
            String reviewedBy,
            @NotBlank
            @Pattern(regexp = "CONFIRMED|CORRECTED|REJECTED") String reviewResult,
            String reviewComment
    ) {
    }

    public record FeedbackRequest(
            @NotBlank String aiResultType,
            @NotBlank String aiResultId,
            @NotNull @Min(1) @Max(5) Integer rating,
            String comment
    ) {
    }

    public record FeedbackResponse(
            String feedbackId,
            String aiResultId
    ) {
    }

    public record FeedbackItem(
            String feedbackId,
            String feedbackBy,
            Integer rating,
            String comment,
            String adminReply
    ) {
    }

    /** 管理员回复反馈（对齐前端 POST /ai/feedback/{id}/reply） */
    public record FeedbackReplyRequest(
            @NotBlank String reply
    ) {
    }

    public record FeedbackReplyResponse(
            String feedbackId,
            String reply,
            String status
    ) {
    }

    public record CallLogItem(
            String logId,
            String type,
            String patientId,
            String relatedId,
            String model,
            Integer latencyMs,
            String riskLevel,
            // 以下为管理后台调用日志页（AiCallLog.vue）所需字段，对齐前端列定义。
            // logNo/serviceType/modelName/costTime 是前端 el-table-column 的 prop 名。
            String logNo,
            String serviceType,
            String modelName,
            String userName,
            Integer inputLength,
            Integer outputLength,
            Integer costTime,
            String status,
            java.time.LocalDateTime createdAt,
            String callerService,
            String traceId,
            String requestId,
            boolean cacheHit,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal estimatedCostYuan
    ) {
    }

    public record StockMoney(BigDecimal value) {
    }
}
