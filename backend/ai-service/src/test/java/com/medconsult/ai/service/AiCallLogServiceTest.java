package com.medconsult.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import com.medconsult.ai.security.AiHeaders;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AiCallLogServiceTest {

    @AfterEach
    void clearRequestState() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void successShouldPublishEstimatedMetricsWithRequestHeadersAndTrace() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());
        bindRequestHeaders("gateway", "2001");
        MDC.put("traceId", "trace-fixed");

        service.success("MEDICAL_RECORD_SUMMARY", "1001", "SUM-1", "test-model",
                "abcd", "abcdefghij", "LOW", 88);

        ArgumentCaptor<AiCallLogMessage> captor = ArgumentCaptor.forClass(AiCallLogMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(MqConstants.EXCHANGE_LOG), eq(MqConstants.RK_AI_CALL_LOG),
                captor.capture());
        AiCallLogMessage message = captor.getValue();
        assertEquals("SUCCESS", message.status());
        assertEquals("gateway", message.callerService());
        assertEquals("trace-fixed", message.traceId());
        assertEquals("2001", message.triggerUserId());
        assertFalse(message.cacheHit());
        assertEquals(1, message.promptTokens());
        assertEquals(3, message.completionTokens());
        assertEquals(4, message.totalTokens());
        assertEquals(new BigDecimal("0.000008"), message.estimatedCostYuan());
    }

    @Test
    void successShouldPublishProvidedCacheHitMetrics() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());

        service.success("SYMPTOM_CHAT", "1001", "CHAT-1", "test-model",
                "cached request", "cached response", "LOW", 12,
                AiCallLogService.AiCallLogMetrics.cacheHitMetrics());

        ArgumentCaptor<AiCallLogMessage> captor = ArgumentCaptor.forClass(AiCallLogMessage.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        AiCallLogMessage message = captor.getValue();
        assertTrue(message.cacheHit());
        assertEquals(0, message.promptTokens());
        assertEquals(0, message.completionTokens());
        assertEquals(0, message.totalTokens());
        assertEquals(0, BigDecimal.ZERO.compareTo(message.estimatedCostYuan()));
    }

    @Test
    void failedShouldPublishFailureWithGeneratedTraceAndDefaultCaller() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());

        service.failed("SYMPTOM_CHAT", "1001", "CHAT-1", "test-model",
                "abcdefgh", 37, new IllegalStateException("llm failed"));

        ArgumentCaptor<AiCallLogMessage> captor = ArgumentCaptor.forClass(AiCallLogMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(MqConstants.EXCHANGE_LOG), eq(MqConstants.RK_AI_CALL_LOG),
                captor.capture());
        AiCallLogMessage message = captor.getValue();
        assertEquals("FAILED", message.status());
        assertEquals("api", message.callerService());
        assertTrue(message.traceId().startsWith("trace-"));
        assertEquals(null, message.triggerUserId());
        assertEquals("llm failed", message.errorMessage());
        assertEquals(2, message.promptTokens());
        assertEquals(0, message.completionTokens());
        assertEquals(2, message.totalTokens());
    }

    @Test
    void rabbitFailureShouldPersistFallbackWithTruncationAndLatencyClamp() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());
        doThrow(new IllegalStateException("rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(AiCallLogMessage.class));
        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.success("SYMPTOM_CHAT", "1001", "CHAT-1", "test-model",
                "r".repeat(600), "s".repeat(600), "LOW", Long.MAX_VALUE);

        AiCallLogEntity entity = captor.getValue();
        assertEquals("SYMPTOM_CHAT", entity.getCallType());
        assertEquals(1001L, entity.getPatientId());
        assertEquals("api", entity.getCallerService());
        assertTrue(entity.getTraceId().startsWith("trace-"));
        assertEquals("DISEASE_JSON", entity.getKnowledgeSource());
        assertEquals(500, entity.getRequestSummary().length());
        assertEquals(500, entity.getResponseSummary().length());
        assertEquals(Integer.MAX_VALUE, entity.getLatencyMs());
        assertEquals("SUCCESS", entity.getStatus());
    }

    @Test
    void redeliveredSameMessageShouldBeInsertedOnceByRequestIdUniqueKey() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());

        Set<String> persistedRequestIds = new HashSet<>();
        AtomicInteger successfulInserts = new AtomicInteger();
        when(mapper.insert(any(AiCallLogEntity.class))).thenAnswer(invocation -> {
            AiCallLogEntity entity = invocation.getArgument(0);
            if (!persistedRequestIds.add(entity.getRequestId())) {
                throw new DuplicateKeyException("duplicate request_id");
            }
            successfulInserts.incrementAndGet();
            return 1;
        });

        AiCallLogMessage message = new AiCallLogMessage(
                "SYMPTOM_CHAT",
                "1001",
                "SESSION-1",
                "test-model",
                "头痛发热",
                "建议就诊",
                "LOW",
                "SUCCESS",
                42,
                null,
                "api",
                "trace-test",
                "2001"
        );

        service.saveFromMessage(message);
        service.saveFromMessage(message);

        assertEquals(1, successfulInserts.get());
        assertEquals(1, persistedRequestIds.size());
    }

    @Test
    void saveFromMessageShouldPersistObservabilityMetrics() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());

        ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        AiCallLogMessage message = new AiCallLogMessage(
                "MEDICAL_RECORD_SUMMARY",
                "1001",
                "SUM1",
                "test-model",
                "record text",
                "{\"summary\":\"ok\"}",
                null,
                "SUCCESS",
                64,
                null,
                "medical-record-service",
                "trace-fixed",
                "2001",
                "REQ-fixed",
                true,
                11,
                17,
                28,
                new BigDecimal("0.001234")
        );

        service.saveFromMessage(message);

        AiCallLogEntity entity = captor.getValue();
        assertEquals("medical-record-service", entity.getCallerService());
        assertEquals("trace-fixed", entity.getTraceId());
        assertEquals("REQ-fixed", entity.getRequestId());
        assertEquals(1, entity.getCacheHit());
        assertEquals(11, entity.getPromptTokens());
        assertEquals(17, entity.getCompletionTokens());
        assertEquals(28, entity.getTotalTokens());
        assertEquals(28, entity.getCostTokens());
        assertEquals(new BigDecimal("0.001234"), entity.getEstimatedCostYuan());
    }

    @Test
    void listShouldExposeTraceRequestCostAndCacheMetrics() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());
        bindAdminUser();

        AiCallLogEntity entity = callLogEntity();
        entity.setEstimatedCostYuan(new BigDecimal("0.001234"));
        Page<AiCallLogEntity> result = Page.of(1, 10);
        result.setTotal(1);
        result.setRecords(List.of(entity));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<CallLogItem> page = service.list(null, null, 1, 10);

        CallLogItem item = page.items().getFirst();
        assertEquals("medical-record-service", item.callerService());
        assertEquals("2001", item.userName());
        assertEquals("trace-fixed", item.traceId());
        assertEquals("REQ-fixed", item.requestId());
        assertTrue(item.cacheHit());
        assertEquals(11, item.promptTokens());
        assertEquals(17, item.completionTokens());
        assertEquals(28, item.totalTokens());
        assertEquals(new BigDecimal("0.001234"), item.estimatedCostYuan());
    }

    @Test
    void listShouldClampPaginationAndExposeZeroMetricsForPatientScope() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());
        bindPatientUser(null, 42L);
        AiCallLogEntity entity = callLogEntity();
        entity.setPatientId(42L);
        entity.setRequestSummary(null);
        entity.setResponseSummary(null);
        entity.setCacheHit(null);
        entity.setPromptTokens(null);
        entity.setCompletionTokens(null);
        entity.setTotalTokens(null);
        entity.setEstimatedCostYuan(null);
        Page<AiCallLogEntity> result = Page.of(1, 100);
        result.setTotal(1);
        result.setRecords(List.of(entity));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(result);

        PageResult<CallLogItem> page = service.list("9999", "SYMPTOM_CHAT", 0, 101);

        assertEquals(1, page.page());
        assertEquals(100, page.pageSize());
        CallLogItem item = page.items().getFirst();
        assertFalse(item.cacheHit());
        assertEquals(0, item.promptTokens());
        assertEquals(0, item.completionTokens());
        assertEquals(0, item.totalTokens());
        assertEquals(new BigDecimal("0.000000"), item.estimatedCostYuan());
        assertEquals(0, item.inputLength());
        assertEquals(0, item.outputLength());
    }

    @Test
    void listShouldRejectAnonymousAndPatientWithoutLinkedIdentity() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiCallLogService service = new AiCallLogService(mapper, rabbitTemplate, aiProperties());

        BusinessException anonymous = assertThrows(BusinessException.class,
                () -> service.list(null, null, 1, 10));
        assertEquals(ErrorCode.UNAUTHORIZED, anonymous.getErrorCode());

        bindPatientUser("PATIENT", null);
        BusinessException missingPatient = assertThrows(BusinessException.class,
                () -> service.list(null, null, 1, 10));
        assertEquals(ErrorCode.FORBIDDEN, missingPatient.getErrorCode());
    }

    private static void bindAdminUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, new JwtPayload(
                JwtPayload.SubjectType.USER,
                9001L,
                null,
                "管理员",
                List.of("HOSPITAL_ADMIN"),
                "HOSPITAL_ADMIN",
                null,
                null,
                null,
                "U9001",
                List.of("*"),
                "jti",
                9999999999L
        ));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void bindPatientUser(String primaryRole, Long patientId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityContext.PAYLOAD_ATTR_KEY, new JwtPayload(
                JwtPayload.SubjectType.USER,
                7001L,
                null,
                "patient",
                List.of("PATIENT"),
                primaryRole,
                patientId,
                null,
                null,
                "U7001",
                List.of("*"),
                "jti",
                9999999999L
        ));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void bindRequestHeaders(String callerService, String triggerUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AiHeaders.CALLER_SERVICE, callerService);
        request.addHeader(AiHeaders.TRIGGER_USER_ID, triggerUserId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static AiCallLogEntity callLogEntity() {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setLogNo("AILOG1");
        entity.setCallType("MEDICAL_RECORD_SUMMARY");
        entity.setPatientId(1001L);
        entity.setRelatedId("SUM1");
        entity.setCallerService("medical-record-service");
        entity.setTriggerUserId(2001L);
        entity.setTraceId("trace-fixed");
        entity.setRequestId("REQ-fixed");
        entity.setCacheHit(1);
        entity.setPromptTokens(11);
        entity.setCompletionTokens(17);
        entity.setTotalTokens(28);
        entity.setCostTokens(28);
        entity.setModelName("test-model");
        entity.setRequestSummary("record text");
        entity.setResponseSummary("{\"summary\":\"ok\"}");
        entity.setStatus("SUCCESS");
        entity.setLatencyMs(64);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        return entity;
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
