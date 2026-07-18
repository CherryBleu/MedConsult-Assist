package com.medconsult.ai.mq;

import com.medconsult.ai.service.AiCallLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiCallLogConsumerTest {

    @Test
    void consumeShouldForwardLegacyMessageWithGeneratedCompatibilityFields() {
        AiCallLogService service = mock(AiCallLogService.class);
        AiCallLogConsumer consumer = new AiCallLogConsumer(service);
        AiCallLogMessage message = new AiCallLogMessage(
                "IMAGE_DETECTION", "PAT1", "DET1", "vision-model",
                "request", "response", "LOW", "SUCCESS", 37L,
                null, "ai-service", "trace-1", "USER2");
        ArgumentCaptor<AiCallLogMessage> captor = ArgumentCaptor.forClass(AiCallLogMessage.class);

        consumer.consume(message);

        verify(service).saveFromMessage(captor.capture());
        AiCallLogMessage forwarded = captor.getValue();
        assertSame(message, forwarded);
        assertTrue(forwarded.requestId().startsWith("REQ"));
        assertFalse(forwarded.cacheHit());
        assertEquals(0, forwarded.totalTokens());
        assertEquals(BigDecimal.ZERO, forwarded.estimatedCostYuan());
    }

    @Test
    void consumeShouldForwardCanonicalMessageAfterNullCostNormalization() {
        AiCallLogService service = mock(AiCallLogService.class);
        AiCallLogConsumer consumer = new AiCallLogConsumer(service);
        AiCallLogMessage message = new AiCallLogMessage(
                "SUMMARY", "PAT1", "SUM1", "llm-model", "request", "response",
                null, "SUCCESS", 12L, null, "medical-record-service", "trace-2", "USER2",
                "REQ-fixed", true, 3, 5, 8, null);
        ArgumentCaptor<AiCallLogMessage> captor = ArgumentCaptor.forClass(AiCallLogMessage.class);

        consumer.consume(message);

        verify(service).saveFromMessage(captor.capture());
        assertEquals(BigDecimal.ZERO, captor.getValue().estimatedCostYuan());
        assertEquals(8, captor.getValue().totalTokens());
    }

    @Test
    void consumeShouldPropagatePersistenceFailureForContainerRetry() {
        AiCallLogService service = mock(AiCallLogService.class);
        AiCallLogConsumer consumer = new AiCallLogConsumer(service);
        AiCallLogMessage message = new AiCallLogMessage(
                "SUMMARY", "PAT1", "SUM1", "model", "request", "response",
                null, "FAILED", 12L, "db error", "ai-service", "trace-3", "USER2");
        doThrow(new IllegalStateException("database unavailable")).when(service).saveFromMessage(message);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> consumer.consume(message));

        assertEquals("database unavailable", failure.getMessage());
        verify(service).saveFromMessage(message);
    }
}
