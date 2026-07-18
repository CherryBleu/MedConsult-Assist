package com.medconsult.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.CallLogItem;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCallLogServiceTest {

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
        try {
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
            entity.setEstimatedCostYuan(new BigDecimal("0.001234"));
            entity.setModelName("test-model");
            entity.setRequestSummary("record text");
            entity.setResponseSummary("{\"summary\":\"ok\"}");
            entity.setStatus("SUCCESS");
            entity.setLatencyMs(64);
            entity.setCreatedAt(java.time.LocalDateTime.now());

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
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
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
