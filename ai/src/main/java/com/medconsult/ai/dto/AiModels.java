package com.medconsult.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
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
            @NotBlank String summaryId,
            @NotBlank String confirmedBy,
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

    public record ImagingDetectionRequest(
            String patientId,
            String recordId,
            @NotBlank String imageType,
            String reportText,
            List<String> imageUrls,
            ExternalModelDto externalModel
    ) {
    }

    public record ExternalModelDto(
            String provider,
            String model
    ) {
    }

    public record ImagingDetectionResponse(
            String detectionId,
            String status,
            boolean abnormalDetected,
            List<Map<String, Object>> findings,
            String disclaimer
    ) {
    }

    public record ImagingReviewRequest(
            @NotBlank String reviewedBy,
            @NotBlank String reviewResult,
            String reviewComment
    ) {
    }

    public record ImagingReviewResponse(
            String detectionId,
            String reviewStatus
    ) {
    }

    public record FeedbackRequest(
            @NotBlank String aiResultType,
            @NotBlank String aiResultId,
            @NotBlank String feedbackBy,
            @NotNull Boolean useful,
            Boolean adopted,
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
            boolean useful,
            boolean adopted,
            String comment
    ) {
    }

    public record CallLogItem(
            String logId,
            String type,
            String patientId,
            String relatedId,
            String model,
            Integer latencyMs,
            String riskLevel
    ) {
    }

    public record StockMoney(BigDecimal value) {
    }
}
