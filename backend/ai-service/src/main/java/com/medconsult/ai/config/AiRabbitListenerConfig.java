package com.medconsult.ai.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AiRabbitListenerConfig {

    public static final String CONTAINER_FACTORY_BEAN_NAME = "aiRabbitListenerContainerFactory";
    private static final String RETRY_INTERCEPTOR_BEAN_NAME = "aiRabbitRetryInterceptor";

    @Bean
    public AiDeadLetterMessageRecoverer aiDeadLetterMessageRecoverer(
            RabbitTemplate rabbitTemplate,
            @Value("${medconsult.ai.mq.retry.max-attempts:3}") int maxAttempts,
            @Value("${medconsult.ai.mq.dead-letter.confirm-timeout:5s}") Duration confirmTimeout) {
        return new AiDeadLetterMessageRecoverer(rabbitTemplate, maxAttempts, confirmTimeout);
    }

    @Bean(RETRY_INTERCEPTOR_BEAN_NAME)
    public RetryOperationsInterceptor aiRabbitRetryInterceptor(
            MessageRecoverer recoverer,
            @Value("${medconsult.ai.mq.retry.max-attempts:3}") int maxAttempts,
            @Value("${medconsult.ai.mq.retry.initial-interval:500ms}") Duration initialInterval,
            @Value("${medconsult.ai.mq.retry.multiplier:2.0}") double multiplier,
            @Value("${medconsult.ai.mq.retry.max-interval:2s}") Duration maxInterval) {
        validateRetrySettings(maxAttempts, initialInterval, multiplier, maxInterval);
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval.toMillis(), multiplier, maxInterval.toMillis())
                .recoverer(recoverer)
                .build();
    }

    @Bean(CONTAINER_FACTORY_BEAN_NAME)
    public SimpleRabbitListenerContainerFactory aiRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier(RETRY_INTERCEPTOR_BEAN_NAME) RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    private static void validateRetrySettings(
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            Duration maxInterval) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("medconsult.ai.mq.retry.max-attempts must be >= 1");
        }
        if (initialInterval == null || initialInterval.toMillis() < 1) {
            throw new IllegalArgumentException("medconsult.ai.mq.retry.initial-interval must be > 0ms");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("medconsult.ai.mq.retry.multiplier must be >= 1.0");
        }
        if (maxInterval == null || maxInterval.toMillis() < 1) {
            throw new IllegalArgumentException("medconsult.ai.mq.retry.max-interval must be > 0ms");
        }
        if (maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalArgumentException(
                    "medconsult.ai.mq.retry.max-interval must be >= initial-interval");
        }
    }
}
