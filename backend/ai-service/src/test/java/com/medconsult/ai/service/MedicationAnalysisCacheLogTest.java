package com.medconsult.ai.service;

import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.PatientContext;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.ai.persistence.mapper.AiMedicationAnalysisMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MedicationAnalysisCacheLogTest {

    @Test
    void analyzeShouldLogCacheHitWithoutCallingLlmOrFunctionTool() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        AiMedicationAnalysisMapper analysisMapper = mock(AiMedicationAnalysisMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        MedicationFunctionService functionService = mock(MedicationFunctionService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        MedicationAnalysisRequest request = request();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(MedicationAnalysisService.medicationCacheKeyFor(request))).thenReturn("""
                {"analysisId":"MA1","overallRiskLevel":"LOW","contraindicationRisks":[],"interactionRisks":[],"reminders":[],"functionTrace":[]}
                """);

        MedicationAnalysisService service = new MedicationAnalysisService(
                llmClient,
                analysisMapper,
                aiProperties(),
                callLogService,
                functionService,
                redisTemplate
        );

        MedicationAnalysisResponse response = service.analyze(request);

        assertEquals("MA1", response.analysisId());
        assertEquals("LOW", response.overallRiskLevel());
        verifyNoInteractions(llmClient, analysisMapper, functionService);

        ArgumentCaptor<AiCallLogService.AiCallLogMetrics> metricsCaptor =
                ArgumentCaptor.forClass(AiCallLogService.AiCallLogMetrics.class);
        verify(callLogService).success(
                eq("MEDICATION_ANALYSIS"),
                eq("1001"),
                eq("MA1"),
                eq("test-model"),
                anyString(),
                anyString(),
                eq("LOW"),
                anyLong(),
                metricsCaptor.capture()
        );
        assertTrue(metricsCaptor.getValue().cacheHit());
        assertEquals(0, metricsCaptor.getValue().totalTokens());
    }

    @Test
    void analyzeShouldReturnCachedResponseWhenCacheHitLoggingFails() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        AiMedicationAnalysisMapper analysisMapper = mock(AiMedicationAnalysisMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        MedicationFunctionService functionService = mock(MedicationFunctionService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        MedicationAnalysisRequest request = request();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(MedicationAnalysisService.medicationCacheKeyFor(request))).thenReturn("""
                {"analysisId":"MA1","overallRiskLevel":"LOW","contraindicationRisks":[],"interactionRisks":[],"reminders":[],"functionTrace":[]}
                """);
        doThrow(new RuntimeException("log unavailable")).when(callLogService).success(
                eq("MEDICATION_ANALYSIS"),
                eq("1001"),
                eq("MA1"),
                eq("test-model"),
                anyString(),
                anyString(),
                eq("LOW"),
                anyLong(),
                org.mockito.ArgumentMatchers.any(AiCallLogService.AiCallLogMetrics.class)
        );

        MedicationAnalysisService service = new MedicationAnalysisService(
                llmClient,
                analysisMapper,
                aiProperties(),
                callLogService,
                functionService,
                redisTemplate
        );

        MedicationAnalysisResponse response = service.analyze(request);

        assertEquals("MA1", response.analysisId());
        assertEquals("LOW", response.overallRiskLevel());
        verifyNoInteractions(llmClient, analysisMapper, functionService);
    }

    private static MedicationAnalysisRequest request() {
        return new MedicationAnalysisRequest(
                "1001",
                "MR1",
                "RX1",
                List.of(new PrescriptionDto("1", "阿莫西林", "0.5g", "bid", "口服", 3)),
                new PatientContext(30, "FEMALE", List.of(), List.of(), List.of()),
                true
        );
    }

    private static AiProperties aiProperties() {
        return new AiProperties(
                new AiProperties.LlmProperties("http://localhost", "test", "test-model", 3),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
