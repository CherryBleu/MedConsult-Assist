package com.medconsult.ai.web;

import com.medconsult.ai.dto.AiModels.AiReviewRequest;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.dto.AiModels.ChunkUploadResponse;
import com.medconsult.ai.dto.AiModels.FeedbackItem;
import com.medconsult.ai.dto.AiModels.FeedbackReplyRequest;
import com.medconsult.ai.dto.AiModels.FeedbackReplyResponse;
import com.medconsult.ai.dto.AiModels.FeedbackRequest;
import com.medconsult.ai.dto.AiModels.FeedbackResponse;
import com.medconsult.ai.dto.AiModels.FileUploadResponse;
import com.medconsult.ai.dto.AiModels.ImageDetectionRequest;
import com.medconsult.ai.dto.AiModels.ImageDetectionResponse;
import com.medconsult.ai.dto.AiModels.ImagingReviewResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextResponse;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
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
import com.medconsult.ai.service.RagReadinessService;
import com.medconsult.ai.service.SummaryService;
import com.medconsult.ai.service.SymptomChatService;
import com.medconsult.ai.service.TriageService;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiControllerDelegationTest {
    private SymptomChatService symptomChatService;
    private TriageService triageService;
    private SummaryService summaryService;
    private MedicationAnalysisService medicationAnalysisService;
    private ImagingDetectionService imagingDetectionService;
    private FeedbackService feedbackService;
    private AiCallLogService callLogService;
    private AiSseService aiSseService;
    private FileUploadService fileUploadService;
    private RagReadinessService ragReadinessService;
    private RagProbeService ragProbeService;
    private AiController controller;

    @BeforeEach
    void setUp() {
        symptomChatService = mock(SymptomChatService.class);
        triageService = mock(TriageService.class);
        summaryService = mock(SummaryService.class);
        medicationAnalysisService = mock(MedicationAnalysisService.class);
        imagingDetectionService = mock(ImagingDetectionService.class);
        feedbackService = mock(FeedbackService.class);
        callLogService = mock(AiCallLogService.class);
        aiSseService = mock(AiSseService.class);
        fileUploadService = mock(FileUploadService.class);
        ragReadinessService = mock(RagReadinessService.class);
        ragProbeService = mock(RagProbeService.class);
        controller = new AiController(
                symptomChatService,
                triageService,
                summaryService,
                medicationAnalysisService,
                imagingDetectionService,
                feedbackService,
                callLogService,
                aiSseService,
                fileUploadService,
                ragReadinessService,
                ragProbeService);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publicEndpointsShouldDelegateToApplicationServices() {
        SymptomChatRequest chatRequest = new SymptomChatRequest("S1", null, "咳嗽", null, null);
        SymptomChatResponse chatResponse = new SymptomChatResponse("S1", "answer", "RULE",
                List.of(), List.of("呼吸内科"), "LOW", false, List.of(), List.of());
        when(symptomChatService.chat(same(chatRequest))).thenReturn(chatResponse);
        SymptomChatService.SessionCreated session = new SymptomChatService.SessionCreated("S1", "咳嗽", "ACTIVE");
        when(symptomChatService.createSession()).thenReturn(session);
        List<SymptomChatService.ChatHistoryItem> history = List.of(
                new SymptomChatService.ChatHistoryItem("USER", "咳嗽", "AI", "建议就诊")
        );
        when(symptomChatService.getHistory("S1")).thenReturn(history);
        RagReadinessService.RagReadiness current = readiness(false);
        RagReadinessService.RagReadiness refreshed = readiness(true);
        when(ragReadinessService.current()).thenReturn(current);
        when(ragReadinessService.refresh()).thenReturn(refreshed);
        RagProbeService.ProbeRun probeRun = new RagProbeService.ProbeRun(true, LocalDateTime.now(), List.of());
        when(ragProbeService.runProbes()).thenReturn(probeRun);

        TriageRequest triageRequest = triageRequest();
        TriageResponse triageResponse = new TriageResponse(false, List.of());
        when(triageService.triage(same(triageRequest))).thenReturn(triageResponse);
        SseEmitter triageEmitter = new SseEmitter();
        when(aiSseService.streamTriage(same(triageRequest))).thenReturn(triageEmitter);

        MedicalRecordSummaryResponse summaryResponse = new MedicalRecordSummaryResponse(
                "SUM-1", "MR1", Map.of("diagnosis", "肺炎"), "GENERATED");
        when(summaryService.summarizeByRecordNo("MR1")).thenReturn(summaryResponse);
        MedicalRecordSummaryTextRequest textRequest = new MedicalRecordSummaryTextRequest("record text", "SOAP");
        MedicalRecordSummaryTextResponse textResponse = new MedicalRecordSummaryTextResponse(Map.of("chief", "咳嗽"), "GENERATED");
        when(summaryService.summarizeTextRequest(same(textRequest))).thenReturn(textResponse);
        SummaryConfirmRequest confirmRequest = new SummaryConfirmRequest("SUM-1", "D1", Map.of("diagnosis", "肺炎"));
        SummaryConfirmResponse confirmResponse = new SummaryConfirmResponse("SUM-1", "MR1", "CONFIRMED");
        when(summaryService.confirm("SUM-1", confirmRequest)).thenReturn(confirmResponse);
        MedicalRecordSummaryRequest summaryRequest = new MedicalRecordSummaryRequest("MR1", "SOAP", false);
        SseEmitter summaryEmitter = new SseEmitter();
        when(aiSseService.streamSummary(same(summaryRequest))).thenReturn(summaryEmitter);

        MedicationAnalysisRequest medicationRequest = medicationRequest();
        MedicationAnalysisResponse medicationResponse = new MedicationAnalysisResponse("MED-1", "LOW",
                List.of(), List.of(), List.of(), List.of());
        when(medicationAnalysisService.analyze(same(medicationRequest))).thenReturn(medicationResponse);
        SseEmitter medicationEmitter = new SseEmitter();
        when(aiSseService.streamMedication(same(medicationRequest))).thenReturn(medicationEmitter);

        ReportAnalysisRequest reportRequest = new ReportAnalysisRequest("42", "MR1", "ATT1", "BLOOD", "正常", null);
        ReportAnalysisResponse reportResponse = new ReportAnalysisResponse("RPT-1", "COMPLETED", false, List.of(), "disclaimer");
        when(imagingDetectionService.analyzeReport(same(reportRequest))).thenReturn(reportResponse);
        AiReviewRequest reviewRequest = new AiReviewRequest("33", "CONFIRMED", "ok");
        ImagingReviewResponse reviewResponse = new ImagingReviewResponse("IMG-1", "REVIEWED", "CONFIRMED", "ok", 33L, null);
        when(imagingDetectionService.reviewReportAnalysis("RPT-1", reviewRequest)).thenReturn(reviewResponse);
        ImageDetectionRequest imageRequest = new ImageDetectionRequest("42", "MR1", "CT", List.of("FILE-1"), List.of(), "MINIO", null);
        ImageDetectionResponse imageResponse = new ImageDetectionResponse("IMG-1", "PENDING", false,
                List.of(), "PENDING", null, null, null, null, "disclaimer");
        when(imagingDetectionService.submitImageDetection(same(imageRequest))).thenReturn(imageResponse);
        when(imagingDetectionService.getImageDetection("IMG-1")).thenReturn(imageResponse);
        when(imagingDetectionService.reviewImageDetection("IMG-1", reviewRequest)).thenReturn(reviewResponse);
        List<ImageDetectionResponse> images = List.of(imageResponse);
        when(imagingDetectionService.listByPatient("42")).thenReturn(images);

        FeedbackRequest feedbackRequest = new FeedbackRequest("TRIAGE", "TRIAGE-1", 5, "helpful");
        FeedbackResponse feedbackResponse = new FeedbackResponse("FB-1", "TRIAGE-1");
        when(feedbackService.submit(same(feedbackRequest))).thenReturn(feedbackResponse);
        List<FeedbackItem> feedbackItems = List.of(new FeedbackItem(
                "FB-1", "TRIAGE", "TRIAGE-1", "42", 5, "helpful", null, LocalDateTime.now(), null));
        when(feedbackService.list("TRIAGE", "TRIAGE-1")).thenReturn(feedbackItems);
        FeedbackReplyRequest replyRequest = new FeedbackReplyRequest("收到");
        FeedbackReplyResponse replyResponse = new FeedbackReplyResponse("FB-1", "收到", "REPLIED");
        when(feedbackService.reply("FB-1", replyRequest)).thenReturn(replyResponse);
        PageResult<CallLogItem> callLogs = PageResult.empty(1, 10);
        when(callLogService.list("42", "TRIAGE", 1, 10)).thenReturn(callLogs);

        MultipartFile file = mock(MultipartFile.class);
        FileUploadResponse fileResponse = new FileUploadResponse("FILE-1", "http://file", 10L,
                "image/png", "MINIO", "bucket", "object", "a.png");
        when(fileUploadService.upload(file, "42", "MR1")).thenReturn(fileResponse);
        ChunkUploadResponse chunkResponse = new ChunkUploadResponse("UP-1", 1, 2, false, null);
        when(fileUploadService.uploadChunk(file, "UP-1", 1, 2, "a.png", "42", "MR1")).thenReturn(chunkResponse);
        when(fileUploadService.getFile("FILE-1")).thenReturn(fileResponse);

        assertSame(chatResponse, controller.symptomChat(chatRequest).data());
        assertSame(session, controller.createSession().data());
        assertSame(history, controller.sessionHistory("S1").data());
        assertSame(current, controller.ragReadiness(false).data());
        assertSame(refreshed, controller.ragReadiness(true).data());
        assertSame(probeRun, controller.ragProbes().data());
        assertSame(triageResponse, controller.triage(triageRequest).data());
        assertSame(triageEmitter, controller.triageStream(triageRequest));
        assertSame(summaryResponse, controller.summaryByRecord("MR1").data());
        assertSame(textResponse, controller.summaryByText(textRequest).data());
        assertSame(confirmResponse, controller.confirmSummary("SUM-1", confirmRequest).data());
        assertSame(summaryEmitter, controller.summaryStream(summaryRequest));
        assertSame(medicationResponse, controller.medicationAnalysis(medicationRequest).data());
        assertSame(medicationEmitter, controller.medicationAnalysisStream(medicationRequest));
        assertSame(reportResponse, controller.reportAnalysis(reportRequest).data());
        assertSame(reviewResponse, controller.reportAnalysisReview("RPT-1", reviewRequest).data());
        assertSame(imageResponse, controller.imagingDetection(imageRequest).data());
        assertSame(imageResponse, controller.imagingDetectionDetail("IMG-1").data());
        assertSame(reviewResponse, controller.imagingDetectionReview("IMG-1", reviewRequest).data());
        assertSame(images, controller.imagingDetectionList("42").data());
        assertSame(feedbackResponse, controller.feedback(feedbackRequest).data());
        assertSame(feedbackItems, controller.feedbackList("TRIAGE", "TRIAGE-1").data());
        assertSame(replyResponse, controller.feedbackReply("FB-1", replyRequest).data());
        assertSame(callLogs, controller.callLogs("42", "TRIAGE", 1, 10).data());
        assertSame(fileResponse, controller.uploadFile(file, "42", "MR1").data());
        assertSame(chunkResponse, controller.uploadFileChunk(file, "UP-1", 1, 2, "a.png", "42", "MR1").data());
        assertSame(fileResponse, controller.getFile("FILE-1").data());
    }

    @Test
    void internalEndpointsShouldRequireServiceAndDelegate() {
        JwtPayload actor = servicePayload("medical-record-service");
        bindPayload(actor);
        MedicationAnalysisRequest medicationRequest = medicationRequest();
        MedicationAnalysisResponse medicationResponse = new MedicationAnalysisResponse("MED-1", "LOW",
                List.of(), List.of(), List.of(), List.of());
        when(medicationAnalysisService.analyze(same(medicationRequest))).thenReturn(medicationResponse);
        SseEmitter medicationEmitter = new SseEmitter();
        when(aiSseService.streamMedication(same(medicationRequest))).thenReturn(medicationEmitter);

        TriageRequest triageRequest = triageRequest();
        TriageResponse triageResponse = new TriageResponse(false, List.of());
        when(triageService.triage(same(triageRequest))).thenReturn(triageResponse);
        SseEmitter triageEmitter = new SseEmitter();
        when(aiSseService.streamTriage(same(triageRequest))).thenReturn(triageEmitter);

        MedicalRecordSummaryRequest summaryRequest = new MedicalRecordSummaryRequest("MR1", "SOAP", false);
        MedicalRecordSummaryResponse summaryResponse = new MedicalRecordSummaryResponse(
                "SUM-1", "MR1", Map.of("diagnosis", "肺炎"), "GENERATED");
        when(summaryService.summarizeRecord(same(summaryRequest), same(actor))).thenReturn(summaryResponse);
        SseEmitter summaryEmitter = new SseEmitter();
        when(aiSseService.streamInternalSummary(same(summaryRequest), same(actor))).thenReturn(summaryEmitter);

        ReportAnalysisRequest reportRequest = new ReportAnalysisRequest("42", "MR1", "ATT1", "BLOOD", "正常", null);
        ReportAnalysisResponse reportResponse = new ReportAnalysisResponse("RPT-1", "COMPLETED", false, List.of(), "disclaimer");
        when(imagingDetectionService.analyzeReport(same(reportRequest))).thenReturn(reportResponse);
        ImageDetectionRequest imageRequest = new ImageDetectionRequest("42", "MR1", "CT", List.of("FILE-1"), List.of(), "MINIO", null);
        ImageDetectionResponse imageResponse = new ImageDetectionResponse("IMG-1", "PENDING", false,
                List.of(), "PENDING", null, null, null, null, "disclaimer");
        when(imagingDetectionService.submitImageDetection(same(imageRequest))).thenReturn(imageResponse);
        when(imagingDetectionService.getImageDetection("IMG-1")).thenReturn(imageResponse);
        MultipartFile file = mock(MultipartFile.class);
        FileUploadResponse fileResponse = new FileUploadResponse("FILE-1", "http://file", 10L,
                "image/png", "MINIO", "bucket", "object", "a.png");
        when(fileUploadService.upload(file, "42", "MR1")).thenReturn(fileResponse);
        ChunkUploadResponse chunkResponse = new ChunkUploadResponse("UP-1", 1, 2, false, null);
        when(fileUploadService.uploadChunk(file, "UP-1", 1, 2, "a.png", "42", "MR1")).thenReturn(chunkResponse);

        assertSame(medicationResponse, controller.internalMedicationAnalysis(medicationRequest).data());
        assertSame(triageResponse, controller.internalTriage(triageRequest).data());
        assertSame(triageEmitter, controller.internalTriageStream(triageRequest));
        assertSame(summaryResponse, controller.internalSummary(summaryRequest).data());
        assertSame(summaryEmitter, controller.internalSummaryStream(summaryRequest));
        assertSame(reportResponse, controller.internalReportAnalysis(reportRequest).data());
        assertSame(imageResponse, controller.internalImageDetection(imageRequest).data());
        assertSame(imageResponse, controller.internalImageDetectionDetail("IMG-1").data());
        assertSame(medicationEmitter, controller.internalMedicationAnalysisStream(medicationRequest));
        assertSame(fileResponse, controller.internalUploadFile(file, "42", "MR1").data());
        assertSame(chunkResponse, controller.internalUploadFileChunk(file, "UP-1", 1, 2, "a.png", "42", "MR1").data());
    }

    private static TriageRequest triageRequest() {
        return new TriageRequest(null, List.of("咳嗽"), "三天", "LOW", null, null, List.of(), false, null);
    }

    private static MedicationAnalysisRequest medicationRequest() {
        return new MedicationAnalysisRequest("42", "MR1", "RX1",
                List.of(new PrescriptionDto("D1", "阿莫西林", "1粒", "每日三次", "口服", 3)),
                null, false);
    }

    private static RagReadinessService.RagReadiness readiness(boolean ready) {
        return new RagReadinessService.RagReadiness(ready, LocalDateTime.now(), List.of());
    }

    private static void bindPayload(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode, List.of(), null,
                null, null, null, null, List.of("ai:write", "ai:read", "file:write"), "jti", Long.MAX_VALUE);
    }
}
