package com.medconsult.common.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedConsultMqAutoConfigurationTest {

    private final MedConsultMqAutoConfiguration configuration = new MedConsultMqAutoConfiguration();

    @Test
    void shouldDeclareDurableAiDeadLetterTopologyWithoutChangingSourceQueueArguments() {
        TopicExchange exchange = configuration.aiDeadLetterExchange();
        Queue queue = configuration.aiDeadLetterQueue();
        Binding binding = configuration.bindAiDeadLetter(queue, exchange);

        assertEquals(MqConstants.EXCHANGE_AI_DEAD_LETTER, exchange.getName());
        assertTrue(exchange.isDurable());
        assertEquals(MqConstants.QUEUE_AI_DEAD_LETTER, queue.getName());
        assertTrue(queue.isDurable());
        assertEquals(MqConstants.QUEUE_AI_DEAD_LETTER, binding.getDestination());
        assertEquals(MqConstants.EXCHANGE_AI_DEAD_LETTER, binding.getExchange());
        assertEquals(MqConstants.RK_AI_DEAD_LETTER, binding.getRoutingKey());

        assertFalse(configuration.aiCallLogQueue().getArguments().containsKey("x-dead-letter-exchange"));
        assertFalse(configuration.aiCallLogQueue().getArguments().containsKey("x-dead-letter-routing-key"));
        assertFalse(configuration.aiImageDetectQueue().getArguments().containsKey("x-dead-letter-exchange"));
        assertFalse(configuration.aiImageDetectQueue().getArguments().containsKey("x-dead-letter-routing-key"));
    }
}
