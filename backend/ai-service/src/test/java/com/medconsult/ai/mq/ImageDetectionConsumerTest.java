package com.medconsult.ai.mq;

import com.medconsult.ai.service.ImagingDetectionService;
import com.medconsult.common.mq.IdempotentConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageDetectionConsumerTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void consumeShouldRunDetectionOnceWithMessageTrace() {
        ImagingDetectionService detectionService = mock(ImagingDetectionService.class);
        IdempotentConsumer idempotentConsumer = mock(IdempotentConsumer.class);
        ImageDetectionConsumer consumer = new ImageDetectionConsumer(detectionService, idempotentConsumer);
        AtomicReference<String> traceDuringProcessing = new AtomicReference<>();
        when(idempotentConsumer.executeOnce(eq("imaging-detect:DET-1"), any())).thenAnswer(invocation -> {
            traceDuringProcessing.set(MDC.get("traceId"));
            return invocation.<Supplier<?>>getArgument(1).get();
        });

        consumer.consume(new ImageDetectionTaskMessage("DET-1", "trace-fixed"));

        verify(detectionService).processImageDetection("DET-1");
        assertEquals("trace-fixed", traceDuringProcessing.get());
        assertNull(MDC.get("traceId"));
    }

    @Test
    void consumeShouldSkipDetectionWhenIdempotencyGuardReportsDuplicate() {
        ImagingDetectionService detectionService = mock(ImagingDetectionService.class);
        IdempotentConsumer idempotentConsumer = mock(IdempotentConsumer.class);
        ImageDetectionConsumer consumer = new ImageDetectionConsumer(detectionService, idempotentConsumer);
        when(idempotentConsumer.executeOnce(eq("imaging-detect:DET-2"), any())).thenReturn(null);

        consumer.consume(new ImageDetectionTaskMessage("DET-2", "trace-duplicate"));

        verify(idempotentConsumer).executeOnce(eq("imaging-detect:DET-2"), any());
        verifyNoInteractions(detectionService);
        assertNull(MDC.get("traceId"));
    }

    @Test
    void consumeShouldGenerateTraceAndPropagateFailedDetectionForContainerRetry() {
        ImagingDetectionService detectionService = mock(ImagingDetectionService.class);
        IdempotentConsumer idempotentConsumer = mock(IdempotentConsumer.class);
        ImageDetectionConsumer consumer = new ImageDetectionConsumer(detectionService, idempotentConsumer);
        AtomicReference<String> generatedTrace = new AtomicReference<>();
        doThrow(new IllegalStateException("vision unavailable"))
                .when(detectionService).processImageDetection("DET-3");
        when(idempotentConsumer.executeOnce(eq("imaging-detect:DET-3"), any())).thenAnswer(invocation -> {
            generatedTrace.set(MDC.get("traceId"));
            return invocation.<Supplier<?>>getArgument(1).get();
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> consumer.consume(new ImageDetectionTaskMessage("DET-3", null)));

        assertEquals("vision unavailable", failure.getMessage());
        verify(detectionService).processImageDetection("DET-3");
        assertTrue(generatedTrace.get().matches("trace-[0-9a-f]{32}"));
        assertNull(MDC.get("traceId"));
    }
}
