package com.medconsult.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * common-mq 自动装配（架构文档 §3.2 / §6 / §4.2）。
 *
 * <p>业务服务引入本模块后：
 * <ul>
 *   <li>声明架构文档 §4.2 的全部 Exchange / Queue / Binding（topic 交换机）</li>
 *   <li>{@link MessageConverter} = Jackson JSON（消息体自动序列化）</li>
 *   <li>{@link IdempotentConsumer}（Redis 幂等助手）</li>
 *   <li>{@link MessageDispatcher}（本地消息表扫描投递，启用 @EnableScheduling）</li>
 * </ul>
 *
 * <p>前提：业务服务须配置 spring.rabbitmq.*（host/port）+ spring.data.redis.* + 数据源。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@EnableScheduling
public class MedConsultMqAutoConfiguration {

    // ===== 消息转换器（JSON） =====
    @Bean
    @ConditionalOnMissingBean
    public MessageConverter medconsultJacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate medconsultRabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(converter);
        return t;
    }

    // ===== Exchange（topic） =====
    @Bean
    public TopicExchange aiImagingExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.EXCHANGE_AI_IMAGING).durable(true).build();
    }

    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.EXCHANGE_NOTIFICATION).durable(true).build();
    }

    @Bean
    public TopicExchange logExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.EXCHANGE_LOG).durable(true).build();
    }

    @Bean
    public TopicExchange drugEventExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.EXCHANGE_DRUG_EVENT).durable(true).build();
    }

    // ===== Queue（durable） =====
    @Bean
    public Queue aiImageDetectQueue() { return new Queue(MqConstants.QUEUE_AI_IMAGE_DETECT, true); }
    @Bean
    public Queue notificationSendQueue() { return new Queue(MqConstants.QUEUE_NOTIFICATION_SEND, true); }
    @Bean
    public Queue auditLogQueue() { return new Queue(MqConstants.QUEUE_AUDIT_LOG, true); }
    @Bean
    public Queue aiCallLogQueue() { return new Queue(MqConstants.QUEUE_AI_CALL_LOG, true); }
    @Bean
    public Queue drugStockAlertQueue() { return new Queue(MqConstants.QUEUE_DRUG_STOCK_ALERT, true); }

    // ===== Binding =====
    @Bean
    public Binding bindAiImageDetect(Queue aiImageDetectQueue, TopicExchange aiImagingExchange) {
        return BindingBuilder.bind(aiImageDetectQueue).to(aiImagingExchange).with(MqConstants.RK_AI_IMAGE_DETECT);
    }
    @Bean
    public Binding bindNotificationSend(Queue notificationSendQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationSendQueue).to(notificationExchange).with(MqConstants.RK_NOTIFICATION_SEND);
    }
    @Bean
    public Binding bindAuditLog(Queue auditLogQueue, TopicExchange logExchange) {
        return BindingBuilder.bind(auditLogQueue).to(logExchange).with(MqConstants.RK_AUDIT_LOG);
    }
    @Bean
    public Binding bindAiCallLog(Queue aiCallLogQueue, TopicExchange logExchange) {
        return BindingBuilder.bind(aiCallLogQueue).to(logExchange).with(MqConstants.RK_AI_CALL_LOG);
    }
    @Bean
    public Binding bindDrugStockAlert(Queue drugStockAlertQueue, TopicExchange drugEventExchange) {
        return BindingBuilder.bind(drugStockAlertQueue).to(drugEventExchange).with(MqConstants.RK_DRUG_STOCK_ALERT);
    }

    // ===== 幂等 + 调度 =====
    @Bean
    @ConditionalOnMissingBean
    public IdempotentConsumer idempotentConsumer(StringRedisTemplate redis) {
        return new IdempotentConsumer(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageDispatcher messageDispatcher() {
        return new MessageDispatcher();
    }
}
