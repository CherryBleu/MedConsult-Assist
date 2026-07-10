package com.medconsult.ai.web;

import com.medconsult.common.core.ApiResponse;
import com.medconsult.common.core.PageResult;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.dto.AiModels.FileUploadResponse;
import com.medconsult.ai.dto.AiModels.ChunkUploadResponse;
import com.medconsult.ai.dto.AiModels.AiReviewRequest;
import com.medconsult.ai.dto.AiModels.ImageDetectionRequest;
import com.medconsult.ai.dto.AiModels.ImageDetectionResponse;
import com.medconsult.ai.dto.AiModels.ImagingReviewResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextResponse;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.ReportAnalysisRequest;
import com.medconsult.ai.dto.AiModels.ReportAnalysisResponse;
import com.medconsult.ai.dto.AiModels.SummaryConfirmRequest;
import com.medconsult.ai.dto.AiModels.SummaryConfirmResponse;
import com.medconsult.ai.dto.AiModels.SymptomChatRequest;
import com.medconsult.ai.dto.AiModels.SymptomChatResponse;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.service.AiCallLogService;
import com.medconsult.ai.service.AiSseService;
import com.medconsult.ai.service.FeedbackService;
import com.medconsult.ai.service.FileUploadService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final AiSseService aiSseService;
    private final FileUploadService fileUploadService;

    public AiController(SymptomChatService symptomChatService, TriageService triageService,
                        SummaryService summaryService, MedicationAnalysisService medicationAnalysisService,
                        ImagingDetectionService imagingDetectionService, FeedbackService feedbackService,
                        AiCallLogService callLogService, AiSseService aiSseService,
                        FileUploadService fileUploadService) {
        this.symptomChatService = symptomChatService;
        this.triageService = triageService;
        this.summaryService = summaryService;
        this.medicationAnalysisService = medicationAnalysisService;
        this.imagingDetectionService = imagingDetectionService;
        this.feedbackService = feedbackService;
        this.callLogService = callLogService;
        this.aiSseService = aiSseService;
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/api/v1/ai/symptom-chat")
    public ApiResponse<SymptomChatResponse> symptomChat(@Valid @RequestBody SymptomChatRequest request) {
        return ApiResponse.success(symptomChatService.chat(request));
    }

    @PostMapping("/api/v1/ai/triage")
    public ApiResponse<TriageResponse> triage(@Valid @RequestBody TriageRequest request) {
        return ApiResponse.success(triageService.triage(request));
    }

    @PostMapping(value = "/api/v1/ai/triage/stream", produces = "text/event-stream")
    public SseEmitter triageStream(@Valid @RequestBody TriageRequest request) {
        return aiSseService.streamTriage(request);
    }

    @PostMapping("/api/v1/ai/medical-record-summary")
    public ApiResponse<MedicalRecordSummaryResponse> summary(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return ApiResponse.success(summaryService.summarizeRecord(request));
    }

    @PostMapping(value = "/api/v1/ai/medical-record-summary/stream", produces = "text/event-stream")
    public SseEmitter summaryStream(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return aiSseService.streamSummary(request);
    }

    @PostMapping("/api/v1/ai/medical-record-summary/text")
    public ApiResponse<MedicalRecordSummaryTextResponse> summaryText(@Valid @RequestBody MedicalRecordSummaryTextRequest request) {
        return ApiResponse.success(summaryService.summarizeTextRequest(request));
    }

    @PostMapping("/api/v1/medical-records/{recordId}/summary/confirm")
    public ApiResponse<SummaryConfirmResponse> confirmSummary(
            @PathVariable("recordId") String recordId,
            @Valid @RequestBody SummaryConfirmRequest request
    ) {
        return ApiResponse.success(summaryService.confirm(recordId, request));
    }

    @PostMapping("/api/v1/ai/medication-analysis")
    public ApiResponse<MedicationAnalysisResponse> medicationAnalysis(@Valid @RequestBody MedicationAnalysisRequest request) {
        return ApiResponse.success(medicationAnalysisService.analyze(request));
    }

    @PostMapping(value = "/api/v1/ai/medication-analysis/stream", produces = "text/event-stream")
    public SseEmitter medicationAnalysisStream(@Valid @RequestBody MedicationAnalysisRequest request) {
        return aiSseService.streamMedication(request);
    }

    @PostMapping("/api/v1/ai/report-analysis")
    public ApiResponse<ReportAnalysisResponse> reportAnalysis(@Valid @RequestBody ReportAnalysisRequest request) {
        return ApiResponse.success(imagingDetectionService.analyzeReport(request));
    }

    @PostMapping("/api/v1/ai/report-analysis/{analysisId}/review")
    public ApiResponse<ImagingReviewResponse> reportAnalysisReview(
            @PathVariable("analysisId") String analysisId,
            @Valid @RequestBody AiReviewRequest request
    ) {
        return ApiResponse.success(imagingDetectionService.reviewReportAnalysis(analysisId, request));
    }

    @PostMapping("/api/v1/ai/image-detection")
    public ApiResponse<ImageDetectionResponse> imageDetection(@Valid @RequestBody ImageDetectionRequest request) {
        return ApiResponse.success(imagingDetectionService.submitImageDetection(request));
    }

    @GetMapping("/api/v1/ai/image-detection/{detectionId}")
    public ApiResponse<ImageDetectionResponse> imageDetectionDetail(@PathVariable("detectionId") String detectionId) {
        return ApiResponse.success(imagingDetectionService.getImageDetection(detectionId));
    }

    @PostMapping("/api/v1/ai/image-detection/{detectionId}/review")
    public ApiResponse<ImagingReviewResponse> imageDetectionReview(
            @PathVariable("detectionId") String detectionId,
            @Valid @RequestBody AiReviewRequest request
    ) {
        return ApiResponse.success(imagingDetectionService.reviewImageDetection(detectionId, request));
    }

    @PostMapping("/api/v1/ai/feedback")
    public ApiResponse<FeedbackResponse> feedback(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.success(feedbackService.submit(request));
    }

    @GetMapping("/api/v1/ai/feedback")
    public ApiResponse<List<FeedbackItem>> feedbackList(
            @RequestParam(name = "aiResultType", required = false) String aiResultType,
            @RequestParam(name = "aiResultId", required = false) String aiResultId
    ) {
        return ApiResponse.success(feedbackService.list(aiResultType, aiResultId));
    }

    @GetMapping("/api/v1/ai/call-logs")
    public ApiResponse<PageResult<CallLogItem>> callLogs(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        return ApiResponse.success(callLogService.list(patientId, type, page, pageSize));
    }

    @PostMapping(value = "/api/v1/files/upload", consumes = "multipart/form-data")
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return ApiResponse.success(fileUploadService.upload(file, patientId, recordId));
    }

    @PostMapping(value = "/api/v1/files/upload/chunk", consumes = "multipart/form-data")
    public ApiResponse<ChunkUploadResponse> uploadFileChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "uploadId", required = false) String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("filename") String filename,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return ApiResponse.success(fileUploadService.uploadChunk(file, uploadId, chunkIndex, totalChunks, filename, patientId, recordId));
    }

    @GetMapping("/api/v1/files/{fileId}")
    public ApiResponse<FileUploadResponse> getFile(@PathVariable("fileId") String fileId) {
        return ApiResponse.success(fileUploadService.getFile(fileId));
    }

    @PostMapping("/internal/ai/medication-analysis")
    public ApiResponse<MedicationAnalysisResponse> internalMedicationAnalysis(@Valid @RequestBody MedicationAnalysisRequest request) {
        return ApiResponse.success(medicationAnalysisService.analyze(request));
    }

    @PostMapping("/internal/ai/triage")
    public ApiResponse<TriageResponse> internalTriage(@Valid @RequestBody TriageRequest request) {
        return ApiResponse.success(triageService.triage(request));
    }

    @PostMapping(value = "/internal/ai/triage/stream", produces = "text/event-stream")
    public SseEmitter internalTriageStream(@Valid @RequestBody TriageRequest request) {
        return aiSseService.streamTriage(request);
    }

    @PostMapping("/internal/ai/medical-record-summary")
    public ApiResponse<MedicalRecordSummaryResponse> internalSummary(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return ApiResponse.success(summaryService.summarizeRecord(request));
    }

    @PostMapping(value = "/internal/ai/medical-record-summary/stream", produces = "text/event-stream")
    public SseEmitter internalSummaryStream(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return aiSseService.streamSummary(request);
    }

    @PostMapping("/internal/ai/report-analysis")
    public ApiResponse<ReportAnalysisResponse> internalReportAnalysis(@Valid @RequestBody ReportAnalysisRequest request) {
        return ApiResponse.success(imagingDetectionService.analyzeReport(request));
    }

    @PostMapping("/internal/ai/image-detection")
    public ApiResponse<ImageDetectionResponse> internalImageDetection(@Valid @RequestBody ImageDetectionRequest request) {
        return ApiResponse.success(imagingDetectionService.submitImageDetection(request));
    }

    @GetMapping("/internal/ai/image-detection/{detectionId}")
    public ApiResponse<ImageDetectionResponse> internalImageDetectionDetail(@PathVariable("detectionId") String detectionId) {
        return ApiResponse.success(imagingDetectionService.getImageDetection(detectionId));
    }

    @PostMapping(value = "/internal/ai/medication-analysis/stream", produces = "text/event-stream")
    public SseEmitter internalMedicationAnalysisStream(@Valid @RequestBody MedicationAnalysisRequest request) {
        return aiSseService.streamMedication(request);
    }

    @PostMapping(value = "/internal/files/upload", consumes = "multipart/form-data")
    public ApiResponse<FileUploadResponse> internalUploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return ApiResponse.success(fileUploadService.upload(file, patientId, recordId));
    }

    @PostMapping(value = "/internal/files/upload/chunk", consumes = "multipart/form-data")
    public ApiResponse<ChunkUploadResponse> internalUploadFileChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "uploadId", required = false) String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("filename") String filename,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return ApiResponse.success(fileUploadService.uploadChunk(file, uploadId, chunkIndex, totalChunks, filename, patientId, recordId));
    }
}
