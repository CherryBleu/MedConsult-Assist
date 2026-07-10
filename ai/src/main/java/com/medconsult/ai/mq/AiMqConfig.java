package com.medconsult.ai.mq;

import com.medconsult.ai.config.AiProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiMqConfig {
    public static final String IMAGE_DETECTION_ROUTING_KEY = "ai.image.detect";
    public static final String CALL_LOG_ROUTING_KEY = "ai.calllog";

    @Bean
    public DirectExchange aiExchange(AiProperties properties) {
        return new DirectExchange(properties.mq().exchange(), true, false);
    }

    @Bean
    public Queue imageDetectionQueue(AiProperties properties) {
        return new Queue(properties.mq().imageDetectionQueue(), true);
    }

    @Bean
    public Queue callLogQueue(AiProperties properties) {
        return new Queue(properties.mq().callLogQueue(), true);
    }

    @Bean
    public Binding imageDetectionBinding(Queue imageDetectionQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(imageDetectionQueue).to(aiExchange).with(IMAGE_DETECTION_ROUTING_KEY);
    }

    @Bean
    public Binding callLogBinding(Queue callLogQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(callLogQueue).to(aiExchange).with(CALL_LOG_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        return factory;
    }
}
