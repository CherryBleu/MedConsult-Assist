package com.medconsult.ai.web;

import com.medconsult.ai.common.ApiResponse;
import com.medconsult.ai.common.PageResult;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.dto.AiModels.ImagingDetectionRequest;
import com.medconsult.ai.dto.AiModels.ImagingDetectionResponse;
import com.medconsult.ai.dto.AiModels.ImagingReviewRequest;
import com.medconsult.ai.dto.AiModels.ImagingReviewResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextResponse;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.SummaryConfirmRequest;
import com.medconsult.ai.dto.AiModels.SummaryConfirmResponse;
import com.medconsult.ai.dto.AiModels.SymptomChatRequest;
import com.medconsult.ai.dto.AiModels.SymptomChatResponse;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.service.AiCallLogService;
import com.medconsult.ai.service.FeedbackService;
import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.ai.service.MedicationAnalysisService;
import com.medconsult.ai.service.SummaryService;
import com.medconsult.ai.service.SymptomChatService;
import com.medconsult.ai.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AiController {
    private final SymptomChatService symptomChatService;
    private final TriageService triageService;
    private final SummaryService summaryService;
    private final MedicationAnalysisService medicationAnalysisService;
    private final ImagingDetectionService imagingDetectionService;
    private final FeedbackService feedbackService;
    private final AiCallLogService callLogService;

    public AiController(SymptomChatService symptomChatService, TriageService triageService,
                        SummaryService summaryService, MedicationAnalysisService medicationAnalysisService,
                        ImagingDetectionService imagingDetectionService, FeedbackService feedbackService,
                        AiCallLogService callLogService) {
        this.symptomChatService = symptomChatService;
        this.triageService = triageService;
        this.summaryService = summaryService;
        this.medicationAnalysisService = medicationAnalysisService;
        this.imagingDetectionService = imagingDetectionService;
        this.feedbackService = feedbackService;
        this.callLogService = callLogService;
    }

    @PostMapping("/ai/symptom-chat")
    public ApiResponse<SymptomChatResponse> symptomChat(@Valid @RequestBody SymptomChatRequest request) {
        return ApiResponse.success(symptomChatService.chat(request));
    }

    @PostMapping("/ai/triage")
    public ApiResponse<TriageResponse> triage(@Valid @RequestBody TriageRequest request) {
        return ApiResponse.success(triageService.triage(request));
    }

    @PostMapping("/ai/medical-record-summary")
    public ApiResponse<MedicalRecordSummaryResponse> summary(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return ApiResponse.success(summaryService.summarizeRecord(request));
    }

    @PostMapping("/ai/medical-record-summary/text")
    public ApiResponse<MedicalRecordSummaryTextResponse> summaryText(@Valid @RequestBody MedicalRecordSummaryTextRequest request) {
        return ApiResponse.success(summaryService.summarizeTextRequest(request));
    }

    @PostMapping("/medical-records/{recordId}/summary/confirm")
    public ApiResponse<SummaryConfirmResponse> confirmSummary(
            @PathVariable("recordId") String recordId,
            @Valid @RequestBody SummaryConfirmRequest request
    ) {
        return ApiResponse.success(summaryService.confirm(recordId, request));
    }

    @PostMapping("/ai/medication-analysis")
    public ApiResponse<MedicationAnalysisResponse> medicationAnalysis(@Valid @RequestBody MedicationAnalysisRequest request) {
        return ApiResponse.success(medicationAnalysisService.analyze(request));
    }

    @PostMapping("/ai/imaging-abnormal-detection")
    public ApiResponse<ImagingDetectionResponse> imagingDetection(@Valid @RequestBody ImagingDetectionRequest request) {
        return ApiResponse.success(imagingDetectionService.detect(request));
    }

    @GetMapping("/ai/imaging-abnormal-detection/{detectionId}")
    public ApiResponse<ImagingDetectionResponse> imagingDetectionDetail(@PathVariable("detectionId") String detectionId) {
        return ApiResponse.success(imagingDetectionService.get(detectionId));
    }

    @PostMapping("/ai/imaging-abnormal-detection/{detectionId}/review")
    public ApiResponse<ImagingReviewResponse> imagingReview(
            @PathVariable("detectionId") String detectionId,
            @Valid @RequestBody ImagingReviewRequest request
    ) {
        return ApiResponse.success(imagingDetectionService.review(detectionId, request));
    }

    @PostMapping("/ai/feedback")
    public ApiResponse<FeedbackResponse> feedback(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.success(feedbackService.submit(request));
    }

    @GetMapping("/ai/feedback")
    public ApiResponse<List<FeedbackItem>> feedbackList(
            @RequestParam(name = "aiResultType", required = false) String aiResultType,
            @RequestParam(name = "aiResultId", required = false) String aiResultId
    ) {
        return ApiResponse.success(feedbackService.list(aiResultType, aiResultId));
    }

    @GetMapping("/ai/call-logs")
    public ApiResponse<PageResult<CallLogItem>> callLogs(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        return ApiResponse.success(callLogService.list(patientId, type, page, pageSize));
    }
}
