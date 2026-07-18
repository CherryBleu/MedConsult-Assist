package com.medconsult.ai.config;

import com.medconsult.ai.mq.AiCallLogConsumer;
import com.medconsult.ai.mq.AiCallLogMessage;
import com.medconsult.ai.mq.ImageDetectionConsumer;
import com.medconsult.ai.mq.ImageDetectionTaskMessage;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRabbitListenerConfigTest {

    @Test
    void retryInterceptorShouldMakeThreeAttemptsThenRecoverOriginalMessage() throws Throwable {
        MessageRecoverer recoverer = mock(MessageRecoverer.class);
        RetryOperationsInterceptor interceptor = new AiRabbitListenerConfig().aiRabbitRetryInterceptor(
                recoverer, 3, Duration.ofMillis(1), 1.0, Duration.ofMillis(1));
        Message message = new Message("payload".getBytes(), new MessageProperties());
        ProxyMethodInvocation invocation = mock(ProxyMethodInvocation.class);
        MethodInvocation retryAttempt = mock(MethodInvocation.class);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        AtomicInteger attempts = new AtomicInteger();
        when(invocation.getArguments()).thenReturn(new Object[]{null, message});
        when(invocation.getMethod()).thenReturn(AiCallLogConsumer.class
                .getDeclaredMethod("consume", AiCallLogMessage.class));
        when(invocation.invocableClone()).thenReturn(retryAttempt);
        when(retryAttempt.proceed()).thenAnswer(ignored -> {
            attempts.incrementAndGet();
            throw failure;
        });

        Object result = interceptor.invoke(invocation);

        assertNull(result);
        assertEquals(3, attempts.get());
        verify(invocation, times(3)).invocableClone();
        verify(retryAttempt, times(3)).proceed();
        verify(recoverer).recover(same(message), same(failure));
    }

    @Test
    void factoryShouldApplyBootDefaultsAndDedicatedRetryAdviceWithoutBroker() {
        SimpleRabbitListenerContainerFactoryConfigurer configurer =
                mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RetryOperationsInterceptor retryInterceptor = mock(RetryOperationsInterceptor.class);

        SimpleRabbitListenerContainerFactory factory = new AiRabbitListenerConfig()
                .aiRabbitListenerContainerFactory(configurer, connectionFactory, retryInterceptor);

        assertNotNull(factory);
        verify(configurer).configure(same(factory), same(connectionFactory));
        assertEquals(1, factory.getAdviceChain().length);
        assertSame(retryInterceptor, factory.getAdviceChain()[0]);
        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(factory, "defaultRequeueRejected"));
    }

    @Test
    void aiConsumersShouldUseDedicatedRetryingContainerFactory() throws NoSuchMethodException {
        RabbitListener callLogListener = AiCallLogConsumer.class
                .getDeclaredMethod("consume", AiCallLogMessage.class)
                .getAnnotation(RabbitListener.class);
        RabbitListener imageListener = ImageDetectionConsumer.class
                .getDeclaredMethod("consume", ImageDetectionTaskMessage.class)
                .getAnnotation(RabbitListener.class);

        assertEquals(AiRabbitListenerConfig.CONTAINER_FACTORY_BEAN_NAME, callLogListener.containerFactory());
        assertEquals(AiRabbitListenerConfig.CONTAINER_FACTORY_BEAN_NAME, imageListener.containerFactory());
    }

    @Test
    void retrySettingsShouldFailFastWithPropertySpecificErrors() {
        AiRabbitListenerConfig configuration = new AiRabbitListenerConfig();
        MessageRecoverer recoverer = mock(MessageRecoverer.class);

        assertAll(
                () -> assertInvalidSettings(configuration, recoverer,
                        0, Duration.ofMillis(1), 1.0, Duration.ofMillis(1), "max-attempts"),
                () -> assertInvalidSettings(configuration, recoverer,
                        1, Duration.ZERO, 1.0, Duration.ofMillis(1), "initial-interval"),
                () -> assertInvalidSettings(configuration, recoverer,
                        1, Duration.ofMillis(1), 0.9, Duration.ofMillis(1), "multiplier"),
                () -> assertInvalidSettings(configuration, recoverer,
                        1, Duration.ofMillis(1), 1.0, Duration.ZERO, "max-interval"),
                () -> assertInvalidSettings(configuration, recoverer,
                        1, Duration.ofMillis(2), 1.0, Duration.ofMillis(1), "max-interval"));
    }

    private static void assertInvalidSettings(
            AiRabbitListenerConfig configuration,
            MessageRecoverer recoverer,
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            Duration maxInterval,
            String propertyName) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> configuration.aiRabbitRetryInterceptor(
                        recoverer, maxAttempts, initialInterval, multiplier, maxInterval));
        assertTrue(failure.getMessage().contains(propertyName));
    }
}
