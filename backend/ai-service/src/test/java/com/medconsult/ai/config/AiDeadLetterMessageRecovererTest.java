package com.medconsult.ai.config;

import com.medconsult.common.mq.MqConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiDeadLetterMessageRecovererTest {

    private static final Duration SHORT_CONFIRM_TIMEOUT = Duration.ofMillis(25);

    @Test
    void recoverShouldWaitForAckAndRepublishUnchangedPayloadAndHeadersWithFailureMetadata() {
        RabbitTemplate rabbitTemplate = confirmingTemplate(true, null);
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, SHORT_CONFIRM_TIMEOUT);
        MessageProperties properties = new MessageProperties();
        properties.setHeader("traceId", "trace-1");
        properties.setConsumerQueue(MqConstants.QUEUE_AI_CALL_LOG);
        byte[] body = "sensitive-payload".getBytes(StandardCharsets.UTF_8);
        Message original = new Message(body, properties);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        recoverer.recover(original, new IllegalStateException("database unavailable"));

        verify(rabbitTemplate).setMandatory(true);
        verify(rabbitTemplate).send(
                eq(MqConstants.EXCHANGE_AI_DEAD_LETTER),
                eq(MqConstants.RK_AI_DEAD_LETTER),
                messageCaptor.capture(),
                any(CorrelationData.class));
        Message deadLetter = messageCaptor.getValue();
        assertArrayEquals(body, deadLetter.getBody());
        assertEquals("trace-1", deadLetter.getMessageProperties().getHeader("traceId"));
        assertEquals(MqConstants.QUEUE_AI_CALL_LOG,
                deadLetter.getMessageProperties().getHeader(AiDeadLetterMessageRecoverer.HEADER_ORIGINAL_QUEUE));
        assertEquals("IllegalStateException: database unavailable",
                deadLetter.getMessageProperties().getHeader(AiDeadLetterMessageRecoverer.HEADER_FAILURE_REASON));
        assertEquals(3, (int) deadLetter.getMessageProperties()
                .<Integer>getHeader(AiDeadLetterMessageRecoverer.HEADER_RETRY_ATTEMPTS));
        assertNull(original.getMessageProperties().getHeader(AiDeadLetterMessageRecoverer.HEADER_FAILURE_REASON));
    }

    @Test
    void recoverShouldPropagateImmediateDeadLetterPublishFailure() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, SHORT_CONFIRM_TIMEOUT);
        Message original = message();
        AmqpException publishFailure = new AmqpException("dead-letter exchange unavailable");
        doThrow(publishFailure).when(rabbitTemplate).send(
                eq(MqConstants.EXCHANGE_AI_DEAD_LETTER),
                eq(MqConstants.RK_AI_DEAD_LETTER),
                any(Message.class),
                any(CorrelationData.class));

        AmqpException actual = assertThrows(AmqpException.class,
                () -> recoverer.recover(original, new IllegalStateException("database unavailable")));

        assertSame(publishFailure, actual);
    }

    @Test
    void recoverShouldRejectBrokerNack() {
        RabbitTemplate rabbitTemplate = confirmingTemplate(false, "disk alarm");
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, SHORT_CONFIRM_TIMEOUT);

        AmqpException failure = assertThrows(AmqpException.class,
                () -> recoverer.recover(message(), new IllegalStateException("database unavailable")));

        assertTrue(failure.getMessage().contains("nack"));
    }

    @Test
    void recoverShouldRejectReturnedUnroutableMessage() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message sent = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    sent,
                    312,
                    "NO_ROUTE",
                    MqConstants.EXCHANGE_AI_DEAD_LETTER,
                    MqConstants.RK_AI_DEAD_LETTER));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(MqConstants.EXCHANGE_AI_DEAD_LETTER),
                eq(MqConstants.RK_AI_DEAD_LETTER),
                any(Message.class),
                any(CorrelationData.class));
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, SHORT_CONFIRM_TIMEOUT);

        AmqpException failure = assertThrows(AmqpException.class,
                () -> recoverer.recover(message(), new IllegalStateException("database unavailable")));

        assertTrue(failure.getMessage().contains("returned"));
    }

    @Test
    void recoverShouldRejectConfirmTimeoutWithoutWaitingForProductionTimeout() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, Duration.ofMillis(1));

        AmqpException failure = assertThrows(AmqpException.class,
                () -> recoverer.recover(message(), new IllegalStateException("database unavailable")));

        assertInstanceOf(TimeoutException.class, failure.getCause());
    }

    @Test
    void recoverShouldRestoreInterruptFlagAndRejectInterruptedConfirmWait() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AiDeadLetterMessageRecoverer recoverer = recoverer(rabbitTemplate, Duration.ofSeconds(1));
        Thread.interrupted();
        Thread.currentThread().interrupt();
        try {
            AmqpException failure = assertThrows(AmqpException.class,
                    () -> recoverer.recover(message(), new IllegalStateException("database unavailable")));

            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void constructorShouldRejectNonPositiveConfirmTimeout() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> recoverer(rabbitTemplate, Duration.ZERO));

        assertTrue(failure.getMessage().contains("confirm-timeout"));
    }

    private static AiDeadLetterMessageRecoverer recoverer(RabbitTemplate rabbitTemplate, Duration timeout) {
        return new AiDeadLetterMessageRecoverer(rabbitTemplate, 3, timeout);
    }

    private static RabbitTemplate confirmingTemplate(boolean ack, String reason) {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(
                eq(MqConstants.EXCHANGE_AI_DEAD_LETTER),
                eq(MqConstants.RK_AI_DEAD_LETTER),
                any(Message.class),
                any(CorrelationData.class));
        return rabbitTemplate;
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(MqConstants.QUEUE_AI_CALL_LOG);
        return new Message("payload".getBytes(StandardCharsets.UTF_8), properties);
    }
}
