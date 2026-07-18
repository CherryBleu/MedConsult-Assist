package com.medconsult.ai.config;

import com.medconsult.common.mq.MqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Republishes exhausted AI listener messages without logging their medical payload. */
public class AiDeadLetterMessageRecoverer implements MessageRecoverer {

    public static final String HEADER_ORIGINAL_QUEUE = "x-medconsult-original-queue";
    public static final String HEADER_FAILURE_REASON = "x-medconsult-failure-reason";
    public static final String HEADER_RETRY_ATTEMPTS = "x-medconsult-retry-attempts";

    private static final int MAX_FAILURE_REASON_LENGTH = 512;
    private static final Logger log = LoggerFactory.getLogger(AiDeadLetterMessageRecoverer.class);

    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;
    private final Duration confirmTimeout;

    public AiDeadLetterMessageRecoverer(
            RabbitTemplate rabbitTemplate,
            int maxAttempts,
            Duration confirmTimeout) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("medconsult.ai.mq.retry.max-attempts must be >= 1");
        }
        if (confirmTimeout == null || confirmTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("medconsult.ai.mq.dead-letter.confirm-timeout must be > 0ms");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = maxAttempts;
        this.confirmTimeout = confirmTimeout;
        this.rabbitTemplate.setMandatory(true);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        String originalQueue = message.getMessageProperties().getConsumerQueue();
        if (originalQueue == null || originalQueue.isBlank()) {
            originalQueue = "unknown";
        }
        Throwable rootCause = rootCause(cause);
        Message deadLetter = MessageBuilder.fromClonedMessage(message)
                .setHeader(HEADER_ORIGINAL_QUEUE, originalQueue)
                .setHeader(HEADER_FAILURE_REASON, failureReason(rootCause))
                .setHeader(HEADER_RETRY_ATTEMPTS, maxAttempts)
                .build();

        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.send(
                MqConstants.EXCHANGE_AI_DEAD_LETTER,
                MqConstants.RK_AI_DEAD_LETTER,
                deadLetter,
                correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("AI dead-letter message was returned by broker, replyCode="
                    + returned.getReplyCode());
        }
        if (confirm == null || !confirm.isAck()) {
            String reason = confirm == null ? "missing confirm" : confirm.getReason();
            throw new AmqpException("AI dead-letter publisher confirm nack: " + reason);
        }
        log.warn("AI message moved to dead-letter queue after retries: originalQueue={} failureType={}",
                originalQueue, rootCause.getClass().getSimpleName());
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for AI dead-letter publisher confirm", ex);
        } catch (TimeoutException ex) {
            throw new AmqpException("Timed out waiting for AI dead-letter publisher confirm", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new AmqpException("Failed while waiting for AI dead-letter publisher confirm", cause);
        }
    }

    private static Throwable rootCause(Throwable cause) {
        Throwable current = cause;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String failureReason(Throwable cause) {
        String message = cause.getMessage();
        String reason = cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= MAX_FAILURE_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
