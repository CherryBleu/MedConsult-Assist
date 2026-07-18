package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.persistence.mapper.AiMedicalSummaryMapper;
import com.medconsult.ai.util.JsonUtils;
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SummaryServiceCacheLogTest {
    private static final String CACHE_KEY = "medconsult:ai:summary:record:MR1";

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
        when(medicalRecordClient.resolveId("MR1")).thenReturn(Result.ok(new EntityIdDTO(101L)));
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record("cough")));
        when(llmClient.chatJson(anyString(), anyString())).thenReturn(Optional.of(summaryJson()));
        bindPatient(22L);
        service = serviceWithModel("test-model");
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void summarizeByRecordNoShouldResolveFetchAndAuthorizeBeforeReturningCacheHit() {
        String cached = generateAndCaptureCache(service, "MR1", CACHE_KEY);
        clearInvocations(llmClient, medicalRecordClient, summaryMapper, callLogService, valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(cached);

        MedicalRecordSummaryResponse response = service.summarizeByRecordNo("MR1");

        assertEquals("MR1", response.recordId());
        verify(medicalRecordClient).resolveId("MR1");
        verify(medicalRecordClient).getFullRecord(101L);
        verify(valueOperations).get(CACHE_KEY);
        verifyNoInteractions(llmClient, summaryMapper);

        ArgumentCaptor<AiCallLogService.AiCallLogMetrics> metricsCaptor =
                ArgumentCaptor.forClass(AiCallLogService.AiCallLogMetrics.class);
        verify(callLogService).success(
                eq("MEDICAL_RECORD_SUMMARY"), eq("22"), eq(response.summaryId()),
                eq("test-model"), eq("MR1"), anyString(), isNull(), anyLong(), metricsCaptor.capture());
        assertTrue(metricsCaptor.getValue().cacheHit());
        assertEquals(0, metricsCaptor.getValue().totalTokens());
    }

    @Test
    void summarizeByRecordNoShouldReturnCachedResponseWhenCacheHitLoggingFails() {
        String cached = generateAndCaptureCache(service, "MR1", CACHE_KEY);
        String cachedSummaryNo = JsonUtils.readTree(cached).path("summaryNo").asText();
        clearInvocations(llmClient, medicalRecordClient, summaryMapper, callLogService, valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(cached);
        doThrow(new RuntimeException("log unavailable")).when(callLogService).success(
                eq("MEDICAL_RECORD_SUMMARY"), eq("22"), eq(cachedSummaryNo),
                eq("test-model"), eq("MR1"), anyString(), isNull(), anyLong(),
                any(AiCallLogService.AiCallLogMetrics.class));

        MedicalRecordSummaryResponse response = service.summarizeByRecordNo("MR1");

        assertEquals(cachedSummaryNo, response.summaryId());
        verifyNoInteractions(llmClient, summaryMapper);
    }

    @Test
    void summarizeByRecordNoShouldTreatLegacyCacheWithoutFingerprintAsMissAndOverwriteIt() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("""
                {"summaryNo":"SUM-OLD","summary":{"diagnosis":["old"]},
                 "status":"GENERATED","patientId":"22"}
                """);

        MedicalRecordSummaryResponse response = service.summarizeByRecordNo("MR1");

        assertFalse("SUM-OLD".equals(response.summaryId()));
        verify(llmClient).chatJson(anyString(), anyString());
        String rewritten = captureLatestCacheWrite(CACHE_KEY);
        JsonNode cached = JsonUtils.readTree(rewritten);
        assertEquals(101L, cached.path("recordId").asLong());
        assertTrue(cached.path("sourceFingerprint").asText().matches("[0-9a-f]{64}"));
    }

    @Test
    void summarizeByRecordNoShouldMissCacheWhenRecordContentChanges() {
        String cached = generateAndCaptureCache(service, "MR1", CACHE_KEY);
        clearInvocations(llmClient, medicalRecordClient, summaryMapper, callLogService, valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(cached);
        when(medicalRecordClient.getFullRecord(101L)).thenReturn(Result.ok(record("persistent fever")));

        service.summarizeByRecordNo("MR1");

        verify(llmClient).chatJson(anyString(), anyString());
        verify(summaryMapper).insert(any());
    }

    @Test
    void summarizeByRecordNoShouldMissCacheWhenModelChanges() {
        String cached = generateAndCaptureCache(service, "MR1", CACHE_KEY);
        clearInvocations(llmClient, medicalRecordClient, summaryMapper, callLogService, valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(cached);

        serviceWithModel("new-model").summarizeByRecordNo("MR1");

        verify(llmClient).chatJson(anyString(), anyString());
        verify(summaryMapper).insert(any());
    }

    @Test
    void numericRecordIdShouldUseCanonicalRecordNumberForCacheKey() {
        service.summarizeByRecordNo("101");

        verify(medicalRecordClient, never()).resolveId(anyString());
        verify(medicalRecordClient).getFullRecord(101L);
        verify(valueOperations).get(CACHE_KEY);
        captureLatestCacheWrite(CACHE_KEY);
    }

    private String generateAndCaptureCache(SummaryService target, String reference, String key) {
        target.summarizeByRecordNo(reference);
        return captureLatestCacheWrite(key);
    }

    private String captureLatestCacheWrite(String key) {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(key), jsonCaptor.capture(), eq(Duration.ofMinutes(30)));
        String value = jsonCaptor.getValue();
        assertNotNull(value);
        return value;
    }

    private SummaryService serviceWithModel(String model) {
        return new SummaryService(
                llmClient, medicalRecordClient, summaryMapper, properties(model), callLogService, redisTemplate);
    }

    private static MedicalRecordFullDTO record(String chiefComplaint) {
        return new MedicalRecordFullDTO(
                "MR1", 22L, 33L, chiefComplaint, "two days", "", "",
                List.of("bronchitis"), List.of(), "rest", "COMPLETED", null, null);
    }

    private static JsonNode summaryJson() {
        return JsonUtils.readTree("""
                {"chiefComplaint":"cough","diagnosis":["bronchitis"],"treatmentPlan":"rest",
                 "medications":[],"followUpAdvice":"review"}
                """);
    }

    private static void bindPatient(Long patientId) {
        JwtPayload payload = new JwtPayload(
                JwtPayload.SubjectType.USER, 7L, null, "patient", List.of("PATIENT"), "PATIENT",
                patientId, null, null, "U7", List.of(), "jti", Long.MAX_VALUE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, payload);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static AiProperties properties(String model) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://localhost", "test", model, 3),
                null, null, null, null, null, null, null, null, null, null);
    }

}
