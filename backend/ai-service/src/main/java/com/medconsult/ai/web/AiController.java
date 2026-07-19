package com.medconsult.ai.web;

import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.dto.AiModels.FileUploadResponse;
import com.medconsult.ai.dto.AiModels.ChunkUploadResponse;
import com.medconsult.ai.dto.AiModels.AiReviewRequest;
import com.medconsult.ai.dto.AiModels.FeedbackReplyRequest;
import com.medconsult.ai.dto.AiModels.FeedbackReplyResponse;
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
import com.medconsult.ai.service.RagProbeService;
import com.medconsult.ai.service.SummaryService;
import com.medconsult.ai.service.RagReadinessService;
import com.medconsult.ai.service.SymptomChatService;
import com.medconsult.ai.service.TriageService;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.DataScope;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.Permission;
import com.medconsult.common.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 辅助问诊接口（对齐前端 src/api/ai.js + ai-manage.js 的路径约定）。
 *
 * <p>对外路径 /api/v1/ai/**（走 Gateway）；内部路径 /internal/ai/**（不配 Gateway 路由，
 * 由 {@link SecurityContext#requireService()} 强制服务身份鉴权，§2.4）。
 *
 * <p>SSE 流式接口（/stream）前端当前用 axios 不消费，保留供后续 EventSource 接入。
 */
/**
 * 类级 @Permission：所有接口至少要求"已登录"（用户或服务身份）。
 * 内部接口 {@code /internal/ai/**} 额外由 {@link SecurityContext#requireService()} 强制服务身份。
 * 管理类接口（反馈回复 / 影像复审 / 报告复审）在方法上用 roles 进一步收紧到 DOCTOR/ADMIN。
 */
@Tag(name = "AI 辅助问诊接口", description = "智能分诊 / 症状对话 / 病历摘要 / 用药分析 / 影像检测 / 反馈 / 调用日志")
@Permission
@RestController
@RequiredArgsConstructor
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
    private final RagReadinessService ragReadinessService;
    private final RagProbeService ragProbeService;

    // ===== 症状对话 =====

    /**
     * 症状自诊问答（架构文档 §5.1 / 修改建议 §2.3）。
     * <p>仅 PATIENT 角色可用，患者身份从 JWT patient_id 取，不信任请求体（SELF 数据范围）。
     * 不依赖预约、挂号或病历；账号未关联患者档案时返回建档提示。
     */
    @Operation(summary = "症状对话（单轮）")
    @Permission(code = "ai:symptom-chat", dataScope = DataScope.SELF, roles = {"PATIENT"})
    @PostMapping("/api/v1/ai/symptom-chat")
    public Result<SymptomChatResponse> symptomChat(@Valid @RequestBody SymptomChatRequest request) {
        return Result.ok(symptomChatService.chat(request));
    }

    /**
     * 创建问诊会话（患者身份从 JWT 取，不接受请求参数传入 patientId）。
     */
    @Operation(summary = "创建问诊会话")
    @Permission(code = "ai:symptom-chat", dataScope = DataScope.SELF, roles = {"PATIENT"})
    @PostMapping("/api/v1/ai/symptom-chat/session")
    public Result<SymptomChatService.SessionCreated> createSession() {
        return Result.ok(symptomChatService.createSession());
    }

    @Operation(summary = "获取会话历史")
    @Permission(code = "ai:symptom-chat", dataScope = DataScope.SELF, roles = {"PATIENT"})
    @GetMapping("/api/v1/ai/symptom-chat/history/{sessionId}")
    public Result<List<SymptomChatService.ChatHistoryItem>> sessionHistory(@PathVariable("sessionId") String sessionId) {
        return Result.ok(symptomChatService.getHistory(sessionId));
    }

    @Operation(summary = "RAG 知识库就绪自检")
    @Permission(roles = {"DOCTOR", "HOSPITAL_ADMIN"})
    @GetMapping("/api/v1/ai/rag/readiness")
    public Result<RagReadinessService.RagReadiness> ragReadiness(
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return Result.ok(refresh ? ragReadinessService.refresh() : ragReadinessService.current());
    }

    @Operation(summary = "RAG 固定症状探针")
    @Permission(roles = {"DOCTOR", "HOSPITAL_ADMIN"})
    @GetMapping("/api/v1/ai/rag/probes")
    public Result<RagProbeService.ProbeRun> ragProbes() {
        return Result.ok(ragProbeService.runProbes());
    }

    // ===== 智能分诊 =====

    @Operation(summary = "智能分诊")
    @Permission(code = "ai:triage", dataScope = DataScope.SELF, roles = {"PATIENT"})
    @PostMapping("/api/v1/ai/triage")
    public Result<TriageResponse> triage(@Valid @RequestBody TriageRequest request) {
        return Result.ok(triageService.triage(request));
    }

    @Operation(summary = "智能分诊（SSE 流式）")
    @PostMapping(value = "/api/v1/ai/triage/stream", produces = "text/event-stream")
    public SseEmitter triageStream(@Valid @RequestBody TriageRequest request) {
        return aiSseService.streamTriage(request);
    }

    // ===== 病历摘要（路径对齐前端 /api/v1/ai/summary/*）=====

    @Operation(summary = "病历摘要（按病历）")
    @Permission(roles = {"PATIENT", "DOCTOR"})
    @PostMapping("/api/v1/ai/summary/by-record/{recordNo}")
    public Result<MedicalRecordSummaryResponse> summaryByRecord(
            @PathVariable("recordNo") String recordNo) {
        return Result.ok(summaryService.summarizeByRecordNo(recordNo));
    }

    @Operation(summary = "病历摘要（按文本）")
    @Permission(roles = {"DOCTOR"})
    @PostMapping("/api/v1/ai/summary/by-text")
    public Result<MedicalRecordSummaryTextResponse> summaryByText(
            @Valid @RequestBody MedicalRecordSummaryTextRequest request) {
        return Result.ok(summaryService.summarizeTextRequest(request));
    }

    @Operation(summary = "病历摘要确认/修正")
    @Permission(code = "ai:summary:confirm", dataScope = DataScope.ALL, roles = {"DOCTOR"})
    @PutMapping("/api/v1/ai/summary/{summaryId}/confirm")
    public Result<SummaryConfirmResponse> confirmSummary(
            @PathVariable("summaryId") String summaryId,
            @Valid @RequestBody SummaryConfirmRequest request) {
        return Result.ok(summaryService.confirm(summaryId, request));
    }

    @Operation(summary = "病历摘要（SSE 流式）")
    @Permission(roles = {"PATIENT", "DOCTOR"})
    @PostMapping(value = "/api/v1/ai/summary/stream", produces = "text/event-stream")
    public SseEmitter summaryStream(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        return aiSseService.streamSummary(request);
    }

    // ===== 用药分析 =====

    @Operation(summary = "用药分析")
    @PostMapping("/api/v1/ai/medication-analysis")
    public Result<MedicationAnalysisResponse> medicationAnalysis(@Valid @RequestBody MedicationAnalysisRequest request) {
        return Result.ok(medicationAnalysisService.analyze(request));
    }

    @Operation(summary = "用药分析（SSE 流式）")
    @PostMapping(value = "/api/v1/ai/medication-analysis/stream", produces = "text/event-stream")
    public SseEmitter medicationAnalysisStream(@Valid @RequestBody MedicationAnalysisRequest request) {
        return aiSseService.streamMedication(request);
    }

    // ===== 影像检测（路径对齐前端 /api/v1/ai/imaging-detection/*）=====

    @Operation(summary = "报告文本分析")
    @PostMapping("/api/v1/ai/report-analysis")
    public Result<ReportAnalysisResponse> reportAnalysis(@Valid @RequestBody ReportAnalysisRequest request) {
        return Result.ok(imagingDetectionService.analyzeReport(request));
    }

    @Operation(summary = "报告文本分析复审")
    @Permission(roles = {"DOCTOR", "HOSPITAL_ADMIN"})
    @PutMapping("/api/v1/ai/report-analysis/{analysisId}/review")
    public Result<ImagingReviewResponse> reportAnalysisReview(
            @PathVariable("analysisId") String analysisId,
            @Valid @RequestBody AiReviewRequest request) {
        return Result.ok(imagingDetectionService.reviewReportAnalysis(analysisId, request));
    }

    @Operation(summary = "影像检测提交")
    @PostMapping("/api/v1/ai/imaging-detection")
    public Result<ImageDetectionResponse> imagingDetection(@Valid @RequestBody ImageDetectionRequest request) {
        return Result.ok(imagingDetectionService.submitImageDetection(request));
    }

    @Operation(summary = "影像检测结果详情")
    @GetMapping("/api/v1/ai/imaging-detection/{detectionId}")
    public Result<ImageDetectionResponse> imagingDetectionDetail(@PathVariable("detectionId") String detectionId) {
        return Result.ok(imagingDetectionService.getImageDetection(detectionId));
    }

    @Operation(summary = "影像检测复审")
    @Permission(roles = {"DOCTOR", "HOSPITAL_ADMIN"})
    @PutMapping("/api/v1/ai/imaging-detection/{detectionId}/review")
    public Result<ImagingReviewResponse> imagingDetectionReview(
            @PathVariable("detectionId") String detectionId,
            @Valid @RequestBody AiReviewRequest request) {
        return Result.ok(imagingDetectionService.reviewImageDetection(detectionId, request));
    }

    @Operation(summary = "影像检测列表")
    @GetMapping("/api/v1/ai/imaging-detection/list")
    public Result<List<ImageDetectionResponse>> imagingDetectionList(
            @RequestParam(name = "patientId", required = false) String patientId) {
        return Result.ok(imagingDetectionService.listByPatient(patientId));
    }

    // ===== 反馈 =====

    @Operation(summary = "提交 AI 反馈")
    @Permission(roles = {"PATIENT", "DOCTOR"})
    @PostMapping("/api/v1/ai/feedback")
    public Result<FeedbackResponse> feedback(@Valid @RequestBody FeedbackRequest request) {
        return Result.ok(feedbackService.submit(request));
    }

    @Operation(summary = "AI 反馈列表")
    @Permission(roles = {"HOSPITAL_ADMIN", "DOCTOR"})
    @GetMapping("/api/v1/ai/feedback")
    public Result<List<FeedbackItem>> feedbackList(
            @RequestParam(name = "aiResultType", required = false) String aiResultType,
            @RequestParam(name = "aiResultId", required = false) String aiResultId
    ) {
        return Result.ok(feedbackService.list(aiResultType, aiResultId));
    }

    @Operation(summary = "管理员回复反馈")
    @Permission(roles = {"HOSPITAL_ADMIN", "DOCTOR"})
    @PostMapping("/api/v1/ai/feedback/{feedbackId}/reply")
    public Result<FeedbackReplyResponse> feedbackReply(
            @PathVariable("feedbackId") String feedbackId,
            @Valid @RequestBody FeedbackReplyRequest request) {
        return Result.ok(feedbackService.reply(feedbackId, request));
    }

    // ===== 调用日志（路径对齐前端 /api/v1/ai/call-log，单数）=====

    @Operation(summary = "AI 调用日志")
    @GetMapping("/api/v1/ai/call-log")
    public Result<PageResult<CallLogItem>> callLogs(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize
    ) {
        return Result.ok(callLogService.list(patientId, type, page, pageSize));
    }

    // ===== 文件上传 =====

    @Operation(summary = "文件上传")
    @Permission(roles = {"PATIENT", "DOCTOR", "HOSPITAL_ADMIN"})
    @PostMapping(value = "/api/v1/files/upload", consumes = "multipart/form-data")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return Result.ok(fileUploadService.upload(file, patientId, recordId));
    }

    @Operation(summary = "分片上传")
    @Permission(roles = {"PATIENT", "DOCTOR", "HOSPITAL_ADMIN"})
    @PostMapping(value = "/api/v1/files/upload/chunk", consumes = "multipart/form-data")
    public Result<ChunkUploadResponse> uploadFileChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "uploadId", required = false) String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("filename") String filename,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        return Result.ok(fileUploadService.uploadChunk(file, uploadId, chunkIndex, totalChunks, filename, patientId, recordId));
    }

    @Operation(summary = "获取文件信息")
    @Permission(roles = {"PATIENT", "DOCTOR", "HOSPITAL_ADMIN"})
    @GetMapping("/api/v1/files/{fileId}")
    public Result<FileUploadResponse> getFile(@PathVariable("fileId") String fileId) {
        return Result.ok(fileUploadService.getFile(fileId));
    }

    // ================================================================
    // 内部接口 /internal/ai/**（不配 Gateway 路由，强制服务身份鉴权）
    // ================================================================

    @PostMapping("/internal/ai/medication-analysis")
    public Result<MedicationAnalysisResponse> internalMedicationAnalysis(@Valid @RequestBody MedicationAnalysisRequest request) {
        SecurityContext.requireService("ai:write");
        return Result.ok(medicationAnalysisService.analyze(request));
    }

    @PostMapping("/internal/ai/triage")
    public Result<TriageResponse> internalTriage(@Valid @RequestBody TriageRequest request) {
        SecurityContext.requireService("ai:write");
        return Result.ok(triageService.triage(request));
    }

    @PostMapping(value = "/internal/ai/triage/stream", produces = "text/event-stream")
    public SseEmitter internalTriageStream(@Valid @RequestBody TriageRequest request) {
        SecurityContext.requireService("ai:write");
        return aiSseService.streamTriage(request);
    }

    @PostMapping("/internal/ai/medical-record-summary")
    public Result<MedicalRecordSummaryResponse> internalSummary(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        JwtPayload actor = SecurityContext.requireService("ai:write");
        return Result.ok(summaryService.summarizeRecord(request, actor));
    }

    @PostMapping(value = "/internal/ai/medical-record-summary/stream", produces = "text/event-stream")
    public SseEmitter internalSummaryStream(@Valid @RequestBody MedicalRecordSummaryRequest request) {
        JwtPayload actor = SecurityContext.requireService("ai:write");
        return aiSseService.streamInternalSummary(request, actor);
    }

    @PostMapping("/internal/ai/report-analysis")
    public Result<ReportAnalysisResponse> internalReportAnalysis(@Valid @RequestBody ReportAnalysisRequest request) {
        SecurityContext.requireService("ai:write");
        return Result.ok(imagingDetectionService.analyzeReport(request));
    }

    @PostMapping("/internal/ai/imaging-detection")
    public Result<ImageDetectionResponse> internalImageDetection(@Valid @RequestBody ImageDetectionRequest request) {
        SecurityContext.requireService("ai:write");
        return Result.ok(imagingDetectionService.submitImageDetection(request));
    }

    @GetMapping("/internal/ai/imaging-detection/{detectionId}")
    public Result<ImageDetectionResponse> internalImageDetectionDetail(@PathVariable("detectionId") String detectionId) {
        SecurityContext.requireService("ai:read");
        return Result.ok(imagingDetectionService.getImageDetection(detectionId));
    }

    @PostMapping(value = "/internal/ai/medication-analysis/stream", produces = "text/event-stream")
    public SseEmitter internalMedicationAnalysisStream(@Valid @RequestBody MedicationAnalysisRequest request) {
        SecurityContext.requireService("ai:write");
        return aiSseService.streamMedication(request);
    }

    @PostMapping(value = "/internal/files/upload", consumes = "multipart/form-data")
    public Result<FileUploadResponse> internalUploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        SecurityContext.requireService("file:write");
        return Result.ok(fileUploadService.upload(file, patientId, recordId));
    }

    @PostMapping(value = "/internal/files/upload/chunk", consumes = "multipart/form-data")
    public Result<ChunkUploadResponse> internalUploadFileChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "uploadId", required = false) String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("filename") String filename,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "recordId", required = false) String recordId
    ) {
        SecurityContext.requireService("file:write");
        return Result.ok(fileUploadService.uploadChunk(file, uploadId, chunkIndex, totalChunks, filename, patientId, recordId));
    }
}
