package com.medconsult.ai.service;

import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryTextRequest;
import com.medconsult.ai.dto.AiModels.SummaryConfirmRequest;
import com.medconsult.ai.persistence.entity.AiMedicalSummaryEntity;
import com.medconsult.ai.persistence.mapper.AiMedicalSummaryMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.MedicalRecordFeignClient;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.MedicalRecordFullDTO;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryServiceSecurityTest {
    private OpenAiCompatibleClient llmClient;
    private MedicalRecordFeignClient medicalRecordClient;
    private AiMedicalSummaryMapper summaryMapper;
    private AiCallLogService callLogService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SummaryService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(OpenAiCompatibleClient.class);
        medicalRecordClient = mock(MedicalRecordFeignClient.class);
        summaryMapper = mock(AiMedicalSummaryMapper.class);
        callLogService = mock(AiCallLogService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new SummaryService(
                llmClient, medicalRecordClient, summaryMapper, properties(), callLogService, redisTemplate);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void summarizeByRecordNoShouldRejectCrossPatientBeforeReadingCache() {
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 11L, null));
        when(medicalRecordClient.resolveId("MR1")).thenReturn(Result.ok(new EntityIdDTO(101L)));
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record("MR1", 22L, 33L)));
        when(valueOperations.get("medconsult:ai:summary:record:MR1")).thenReturn("""
                {"summaryNo":"SUM-OTHER","summary":{"diagnosis":["private"]},"status":"GENERATED",
                 "patientId":"22","recordId":101,"sourceFingerprint":"fingerprint"}
                """);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.summarizeByRecordNo("MR1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(valueOperations, never()).get(any());
        verify(summaryMapper, never()).insert(any());
    }

    @Test
    void confirmShouldIgnoreForgedBodyIdentityAndUseJwtDoctorId() {
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, 44L));
        AiMedicalSummaryEntity stored = new AiMedicalSummaryEntity();
        stored.setSummaryNo("SUM-PATH");
        stored.setRecordId(101L);
        when(summaryMapper.selectOne(any())).thenReturn(stored);
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record("MR1", 22L, 44L)));

        service.confirm("SUM-PATH", new SummaryConfirmRequest(
                "SUM-FORGED", "999", Map.of("diagnosis", List.of("confirmed"))));

        ArgumentCaptor<AiMedicalSummaryEntity> entityCaptor =
                ArgumentCaptor.forClass(AiMedicalSummaryEntity.class);
        verify(summaryMapper).updateById(entityCaptor.capture());
        assertEquals(44L, entityCaptor.getValue().getConfirmedBy());
    }

    @Test
    void confirmShouldRejectUnauthenticatedCallerBeforeLookingUpSummary() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm("SUM-SECRET", new SummaryConfirmRequest(
                        null, null, Map.of("diagnosis", List.of("confirmed")))));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verify(summaryMapper, never()).selectOne(any());
    }

    @Test
    void summarizeByRecordNoShouldReportAiFailureWhenLlmConfigurationIsMissing() {
        service = new SummaryService(
                llmClient, medicalRecordClient, summaryMapper, propertiesWithoutLlm(),
                callLogService, redisTemplate);
        bindPayload(userPayload("PATIENT", List.of("PATIENT"), 22L, null));
        when(medicalRecordClient.resolveId("MR1")).thenReturn(Result.ok(new EntityIdDTO(101L)));
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record("MR1", 22L, 33L)));
        when(llmClient.chatJson(any(), any())).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.summarizeByRecordNo("MR1"));

        assertEquals(ErrorCode.AI_EXTERNAL_FAILED, error.getErrorCode());
    }

    @Test
    void summarizeByRecordNoShouldAllowOwningDoctorAndRejectCrossDoctor() {
        stubRecord("MR1", record("MR1", 22L, 33L));
        when(llmClient.chatJson(any(), any())).thenReturn(Optional.of(summaryJson()));
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, 33L));

        service.summarizeByRecordNo("MR1");

        verify(summaryMapper).insert(any());
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, 44L));
        BusinessException crossDoctor = assertThrows(BusinessException.class,
                () -> service.summarizeByRecordNo("MR1"));
        assertEquals(ErrorCode.FORBIDDEN, crossDoctor.getErrorCode());
    }

    @Test
    void summarizeByRecordNoShouldHonorPrimaryRoleInsteadOfSecondaryDoctorRole() {
        stubRecord("MR1", record("MR1", 22L, 33L));
        bindPayload(userPayload("PATIENT", List.of("PATIENT", "DOCTOR"), 99L, 33L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.summarizeByRecordNo("MR1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(valueOperations, never()).get(any());
    }

    @Test
    void summarizeByRecordNoShouldFallbackToRolesForLegacyTokenWithoutPrimaryRole() {
        stubRecord("MR1", record("MR1", 22L, 33L));
        when(llmClient.chatJson(any(), any())).thenReturn(Optional.of(summaryJson()));
        bindPayload(userPayload(null, List.of("DOCTOR"), null, 33L));

        service.summarizeByRecordNo("MR1");

        verify(summaryMapper).insert(any());
    }

    @Test
    void summarizeByRecordNoShouldRejectAdminPharmacyAndServiceActors() {
        stubRecord("MR1", record("MR1", 22L, 33L));

        bindPayload(userPayload("HOSPITAL_ADMIN", List.of("HOSPITAL_ADMIN"), null, null));
        assertEquals(ErrorCode.FORBIDDEN, assertSummaryDenied().getErrorCode());

        bindPayload(userPayload("PHARMACY_ADMIN", List.of("PHARMACY_ADMIN"), null, null));
        assertEquals(ErrorCode.FORBIDDEN, assertSummaryDenied().getErrorCode());

        bindPayload(servicePayload("medical-record-service"));
        assertEquals(ErrorCode.UNAUTHORIZED, assertSummaryDenied().getErrorCode());
        verify(valueOperations, never()).get(any());
    }

    @Test
    void summarizeByRecordNoShouldRejectActivePatientOrDoctorWithoutAssociatedId() {
        stubRecord("MR1", record("MR1", 22L, 33L));

        bindPayload(userPayload("PATIENT", List.of("PATIENT"), null, null));
        assertEquals(ErrorCode.UNAUTHORIZED, assertSummaryDenied().getErrorCode());

        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, null));
        assertEquals(ErrorCode.UNAUTHORIZED, assertSummaryDenied().getErrorCode());
        verify(valueOperations, never()).get(any());
    }

    @Test
    void summarizeTextShouldAllowOnlyActiveDoctorWithDoctorId() {
        when(llmClient.chatJson(any(), any())).thenReturn(Optional.of(summaryJson()));
        MedicalRecordSummaryTextRequest request =
                new MedicalRecordSummaryTextRequest("chief complaint: cough", "STRUCTURED");
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, 33L));

        service.summarizeTextRequest(request);

        bindPayload(userPayload("PATIENT", List.of("PATIENT", "DOCTOR"), 22L, 33L));
        BusinessException patientError = assertThrows(BusinessException.class,
                () -> service.summarizeTextRequest(request));
        bindPayload(userPayload("DOCTOR", List.of("DOCTOR"), null, null));
        BusinessException missingDoctor = assertThrows(BusinessException.class,
                () -> service.summarizeTextRequest(request));

        assertEquals(ErrorCode.FORBIDDEN, patientError.getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED, missingDoctor.getErrorCode());
        verify(llmClient).chatJson(any(), any());
    }

    private BusinessException assertSummaryDenied() {
        return assertThrows(BusinessException.class, () -> service.summarizeByRecordNo("MR1"));
    }

    private void stubRecord(String reference, MedicalRecordFullDTO record) {
        when(medicalRecordClient.resolveId(reference)).thenReturn(Result.ok(new EntityIdDTO(101L)));
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record));
    }

    private static MedicalRecordFullDTO record(String recordNo, Long patientId, Long doctorId) {
        return new MedicalRecordFullDTO(
                recordNo, patientId, doctorId, "cough", "two days", "", "",
                List.of("bronchitis"), List.of(), "rest", "COMPLETED", null, null);
    }

    private static void bindPayload(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static JwtPayload userPayload(String primaryRole, List<String> roles,
                                          Long patientId, Long doctorId) {
        return new JwtPayload(
                JwtPayload.SubjectType.USER, 7L, null, "test-user", roles, primaryRole,
                patientId, doctorId, null, "U7", List.of(), "jti", Long.MAX_VALUE);
    }

    private static JwtPayload servicePayload(String serviceCode) {
        return new JwtPayload(
                JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode, List.of(), null,
                null, null, null, null, List.of(), "jti", Long.MAX_VALUE);
    }

    private static com.fasterxml.jackson.databind.JsonNode summaryJson() {
        return com.medconsult.ai.util.JsonUtils.readTree("""
                {"chiefComplaint":"cough","diagnosis":["bronchitis"],"treatmentPlan":"rest",
                 "medications":[],"followUpAdvice":"review"}
                """);
    }

    private static AiProperties properties() {
        return new AiProperties(
                new AiProperties.LlmProperties("http://localhost", "test", "test-model", 3),
                null, null, null, null, null, null, null, null, null, null);
    }

    private static AiProperties propertiesWithoutLlm() {
        return new AiProperties(
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
