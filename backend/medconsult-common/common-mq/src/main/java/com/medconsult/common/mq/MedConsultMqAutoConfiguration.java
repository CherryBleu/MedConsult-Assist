package com.medconsult.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        // ConfirmCallback 由 MessageDispatcher.bindRabbitTemplate 装配时注入（见 messageDispatcher Bean）。
        // 前提：各服务 application.yml 须配 spring.rabbitmq.publisher-confirm-type: correlated，
        // 否则 ConfirmCallback 不触发，消息停留在 SENT 状态（不丢，但 CONFIRMED 推进不了）。
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

    /**
     * 审计生产端（@AuditLog 切面 + Producer）。
     *
     * <p>仅 SERVLET web 应用装配——切面依赖 HttpServletRequest/SecurityContext，
     * gateway 是 WebFlux 反应式栈（无 servlet），不能装配（否则类加载/Bean 创建失败）。
     * producer 顺带在此注册（common-mq 包默认不被业务 @SpringBootApplication 扫描）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class AuditLogConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AuditLogProducer auditLogProducer(LocalMessageMapper localMessageMapper, ObjectMapper objectMapper) {
            return new AuditLogProducer(localMessageMapper, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        public AuditLogAspect auditLogAspect(AuditLogProducer auditLogProducer) {
            AuditLogAspect aspect = new AuditLogAspect();
            // @Autowired 字段注入（AuditLogAspect 用字段注入以对齐 PermissionAspect 风格）
            aspect.setAuditLogProducer(auditLogProducer);
            return aspect;
        }
    }
}
