package com.medconsult.ai.service;

import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.persistence.mapper.AiMedicalSummaryMapper;
import com.medconsult.common.feign.client.MedicalRecordFeignClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SummaryServiceCacheLogTest {

    @Test
    void summarizeByRecordNoShouldLogCacheHitWithoutCallingLlm() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MedicalRecordFeignClient medicalRecordClient = mock(MedicalRecordFeignClient.class);
        AiMedicalSummaryMapper summaryMapper = mock(AiMedicalSummaryMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("medconsult:ai:summary:record:MR1")).thenReturn("""
                {"summaryNo":"SUM1","summary":{"diagnosis":["急性支气管炎"]},"status":"GENERATED","patientId":"1001"}
                """);

        SummaryService service = new SummaryService(
                llmClient,
                medicalRecordClient,
                summaryMapper,
                aiProperties(),
                callLogService,
                redisTemplate
        );

        MedicalRecordSummaryResponse response = service.summarizeByRecordNo("MR1");

        assertEquals("SUM1", response.summaryId());
        assertEquals("MR1", response.recordId());
        verifyNoInteractions(llmClient, medicalRecordClient, summaryMapper);

        ArgumentCaptor<AiCallLogService.AiCallLogMetrics> metricsCaptor =
                ArgumentCaptor.forClass(AiCallLogService.AiCallLogMetrics.class);
        verify(callLogService).success(
                eq("MEDICAL_RECORD_SUMMARY"),
                eq("1001"),
                eq("SUM1"),
                eq("test-model"),
                eq("MR1"),
                anyString(),
                isNull(),
                anyLong(),
                metricsCaptor.capture()
        );
        assertTrue(metricsCaptor.getValue().cacheHit());
        assertEquals(0, metricsCaptor.getValue().totalTokens());
    }

    @Test
    void summarizeByRecordNoShouldReturnCachedResponseWhenCacheHitLoggingFails() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MedicalRecordFeignClient medicalRecordClient = mock(MedicalRecordFeignClient.class);
        AiMedicalSummaryMapper summaryMapper = mock(AiMedicalSummaryMapper.class);
        AiCallLogService callLogService = mock(AiCallLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("medconsult:ai:summary:record:MR1")).thenReturn("""
                {"summaryNo":"SUM1","summary":{"diagnosis":["急性支气管炎"]},"status":"GENERATED","patientId":"1001"}
                """);
        doThrow(new RuntimeException("log unavailable")).when(callLogService).success(
                eq("MEDICAL_RECORD_SUMMARY"),
                eq("1001"),
                eq("SUM1"),
                eq("test-model"),
                eq("MR1"),
                anyString(),
                isNull(),
                anyLong(),
                org.mockito.ArgumentMatchers.any(AiCallLogService.AiCallLogMetrics.class)
        );

        SummaryService service = new SummaryService(
                llmClient,
                medicalRecordClient,
                summaryMapper,
                aiProperties(),
                callLogService,
                redisTemplate
        );

        MedicalRecordSummaryResponse response = service.summarizeByRecordNo("MR1");

        assertEquals("SUM1", response.summaryId());
        assertEquals("MR1", response.recordId());
        verifyNoInteractions(llmClient, medicalRecordClient, summaryMapper);
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
