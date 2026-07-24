package com.medconsult.common.mq;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDispatcherTest {

    @Test
    void dispatchUsesConfiguredRabbitTemplateConverterAndPreservesMessageMetadata() {
        LocalMessageMapper localMessageMapper = mock(LocalMessageMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessageDispatcher dispatcher = new MessageDispatcher();
        ReflectionTestUtils.setField(dispatcher, "localMessageMapper", localMessageMapper);
        dispatcher.bindRabbitTemplate(rabbitTemplate);

        LocalMessage message = LocalMessage.of(
                MqConstants.EXCHANGE_NOTIFICATION,
                "dispatch-test-message",
                MqConstants.RK_NOTIFICATION_SEND,
                "{\"k\":\"v\"}");
        message.setId(42L);

        when(localMessageMapper.selectList(any())).thenReturn(List.of(message));
        when(localMessageMapper.casUpdateStatus(eq(42L), any(), eq(LocalMessage.STATUS_SENT), any()))
                .thenReturn(1);

        dispatcher.dispatch();

        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        ArgumentCaptor<Message> payloadCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MqConstants.EXCHANGE_NOTIFICATION),
                eq(MqConstants.RK_NOTIFICATION_SEND),
                payloadCaptor.capture(),
                postProcessorCaptor.capture(),
                correlationCaptor.capture());

        Message payload = payloadCaptor.getValue();
        assertEquals("{\"k\":\"v\"}", new String(payload.getBody(), StandardCharsets.UTF_8));
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, payload.getMessageProperties().getContentType());
        Message processed = postProcessorCaptor.getValue()
                .postProcessMessage(MessageBuilder.withBody(new byte[0]).build());
        assertEquals("dispatch-test-message",
                processed.getMessageProperties().getHeaders().get("messageNo"));
        assertEquals("42", correlationCaptor.getValue().getId());
    }
}
