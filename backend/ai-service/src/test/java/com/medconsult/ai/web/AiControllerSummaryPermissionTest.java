package com.medconsult.ai.web;

import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.service.AiCallLogService;
import com.medconsult.ai.service.AiSseService;
import com.medconsult.ai.service.FeedbackService;
import com.medconsult.ai.service.FileUploadService;
import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.ai.service.MedicationAnalysisService;
import com.medconsult.ai.service.RagReadinessService;
import com.medconsult.ai.service.SummaryService;
import com.medconsult.ai.service.SymptomChatService;
import com.medconsult.ai.service.TriageService;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.DataScope;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.Permission;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerSummaryPermissionTest {
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
                ragReadinessService);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publicSummaryEndpointsShouldDeclareRestrictedRoles() throws Exception {
        assertRoles(permission("summaryByRecord", String.class), "PATIENT", "DOCTOR");
        assertRoles(permission("summaryByText",
                com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest.class), "DOCTOR");
        Permission confirm = permission("confirmSummary", String.class,
                com.medconsult.ai.dto.AiModels.SummaryConfirmRequest.class);
        assertEquals("ai:summary:confirm", confirm.code());
        assertEquals(DataScope.ALL, confirm.dataScope());
        assertRoles(confirm, "DOCTOR");
        assertRoles(permission("summaryStream", MedicalRecordSummaryRequest.class), "PATIENT", "DOCTOR");
    }

    @Test
    void internalSummaryShouldPassServiceActorToSummaryService() {
        JwtPayload actor = servicePayload("medical-record-service");
        bindPayload(actor);
        MedicalRecordSummaryRequest request = new MedicalRecordSummaryRequest("101", null, false);
        MedicalRecordSummaryResponse response = new MedicalRecordSummaryResponse(
                "SUM-1", "MR1", Map.of("diagnosis", List.of("bronchitis")), "GENERATED");
        when(summaryService.summarizeRecord(same(request), same(actor))).thenReturn(response);

        assertSame(response, controller.internalSummary(request).data());
    }

    @Test
    void internalSummaryShouldRejectUserActorBeforeCallingService() {
        bindPayload(new JwtPayload(
                JwtPayload.SubjectType.USER, 7L, null, "doctor", List.of("DOCTOR"), "DOCTOR",
                null, 33L, null, "U7", List.of(), "jti", Long.MAX_VALUE));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.internalSummary(new MedicalRecordSummaryRequest("101", null, false)));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verify(summaryService, never()).summarizeRecord(any(), any());
    }

    @Test
    void internalSummaryStreamShouldPassServiceActorToSseService() {
        JwtPayload actor = servicePayload("medical-record-service");
        bindPayload(actor);
        MedicalRecordSummaryRequest request = new MedicalRecordSummaryRequest("101", null, false);
        SseEmitter emitter = new SseEmitter();
        when(aiSseService.streamInternalSummary(same(request), same(actor))).thenReturn(emitter);

        assertSame(emitter, controller.internalSummaryStream(request));
    }

    private static Permission permission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AiController.class.getMethod(methodName, parameterTypes);
        Permission permission = method.getAnnotation(Permission.class);
        assertNotNull(permission);
        return permission;
    }

    private static void assertRoles(Permission permission, String... roles) {
        assertArrayEquals(roles, permission.roles());
    }

    private static void bindPayload(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode, List.of(), null,
                null, null, null, null, List.of(), "jti", Long.MAX_VALUE);
    }
}
