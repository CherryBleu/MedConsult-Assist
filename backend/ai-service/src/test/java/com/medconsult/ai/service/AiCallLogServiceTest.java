package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.persistence.entity.AiCallLogEntity;
import com.medconsult.ai.persistence.mapper.AiCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
