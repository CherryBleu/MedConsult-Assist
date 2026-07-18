package com.medconsult.common.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentConsumerTest {

    private static final String MESSAGE_NO = "imaging-detect:DET-1";
    private static final String KEY = MqConstants.IDEMPOTENT_KEY_PREFIX + MESSAGE_NO;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);
    private static final Duration DONE_WINDOW = Duration.ofHours(72);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private IdempotentConsumer consumer;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockedValues = mock(ValueOperations.class);
        values = mockedValues;
        when(redis.opsForValue()).thenReturn(values);
        consumer = new IdempotentConsumer(redis);
    }

    @Test
    void successfulActionShouldPromoteProcessingLeaseToDoneWindow() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(true);

        Integer result = consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> 7);

        assertEquals(7, result);
        verify(values).set(KEY, "DONE", DONE_WINDOW);
    }

    @Test
    void doneMessageShouldBeSkipped() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(false);
        when(values.get(KEY)).thenReturn("DONE");
        AtomicInteger actions = new AtomicInteger();

        Integer result = consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, actions::incrementAndGet);

        assertNull(result);
        assertEquals(0, actions.get());
    }

    @Test
    void legacyProcessedMarkerShouldRemainCompatible() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(false);
        when(values.get(KEY)).thenReturn("1");

        assertNull(consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> 1));
    }

    @Test
    void processingMessageShouldThrowInsteadOfBeingAcknowledgedAsDuplicate() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(false);
        when(values.get(KEY)).thenReturn("PROCESSING");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> 1));

        assertTrue(failure.getMessage().contains("processing"));
    }

    @Test
    void runtimeFailureShouldRemoveMarkerAndAllowNextAttempt() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(true, true);
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> consumer.executeOnce(
                MESSAGE_NO,
                DONE_WINDOW,
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("vision unavailable");
                }));
        Integer result = consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, attempts::incrementAndGet);

        assertEquals(2, result);
        verify(redis).delete(KEY);
        verify(values).set(KEY, "DONE", DONE_WINDOW);
    }

    @Test
    void errorShouldAlsoRemoveMarkerAndBeRethrown() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(true);
        AssertionError actionFailure = new AssertionError("model process exhausted memory");

        AssertionError actual = assertThrows(AssertionError.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> {
                    throw actionFailure;
                }));

        assertSame(actionFailure, actual);
        verify(redis).delete(KEY);
    }

    @Test
    void deleteFailureShouldKeepProcessingStateAndNeverAcknowledgeNextDelivery() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(true, false);
        when(values.get(KEY)).thenReturn("PROCESSING");
        IllegalStateException actionFailure = new IllegalStateException("vision unavailable");
        IllegalStateException deleteFailure = new IllegalStateException("redis unavailable");
        doThrow(deleteFailure).when(redis).delete(KEY);

        IllegalStateException first = assertThrows(IllegalStateException.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> {
                    throw actionFailure;
                }));
        IllegalStateException nextDelivery = assertThrows(IllegalStateException.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, () -> 1));

        assertSame(actionFailure, first);
        assertEquals(1, first.getSuppressed().length);
        assertSame(deleteFailure, first.getSuppressed()[0]);
        assertTrue(nextDelivery.getMessage().contains("processing"));
    }

    @Test
    void doneWriteFailureShouldKeepProcessingLeaseAndNotRepeatActionImmediately() {
        when(values.setIfAbsent(KEY, "PROCESSING", PROCESSING_LEASE)).thenReturn(true, false);
        when(values.get(KEY)).thenReturn("PROCESSING");
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).set(KEY, "DONE", DONE_WINDOW);
        AtomicInteger actions = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, actions::incrementAndGet));
        assertThrows(IllegalStateException.class,
                () -> consumer.executeOnce(MESSAGE_NO, DONE_WINDOW, actions::incrementAndGet));

        assertEquals(1, actions.get());
        verify(redis, never()).delete(KEY);
    }

    @Test
    void legacyHelpersShouldOnlyTreatDoneStatesAsProcessed() {
        when(values.get(KEY)).thenReturn("PROCESSING", "DONE", "1", null);

        assertFalse(consumer.isAlreadyProcessed(MESSAGE_NO));
        assertTrue(consumer.isAlreadyProcessed(MESSAGE_NO));
        assertTrue(consumer.isAlreadyProcessed(MESSAGE_NO));
        assertFalse(consumer.isAlreadyProcessed(MESSAGE_NO));

        consumer.markProcessed(MESSAGE_NO, DONE_WINDOW);
        verify(values).set(KEY, "DONE", DONE_WINDOW);
    }
}
