package com.medconsult.common.mq;

/**
 * RabbitMQ Exchange / Queue / RoutingKey 常量（架构文档 §4.2）。
 *
 * <p>架构文档 §4.2 定义的队列清单：
 * <ul>
 *   <li>ai.imaging.exchange → ai.image.detect.queue（影像检测异步任务）</li>
 *   <li>notification.exchange → notification.send.queue（通知异步投递）</li>
 *   <li>log.exchange → audit.log.queue（审计日志异步落库）</li>
 *   <li>log.exchange → ai.calllog.queue（AI 调用日志异步落库）</li>
 *   <li>drug.event.exchange → drug.stock.alert.queue（库存预警异步计算）</li>
 *   <li>ai.dead-letter exchange → ai.dead-letter queue（AI 消费重试耗尽消息）</li>
 * </ul>
 *
 * <p>Exchange 统一 topic 类型，便于路由扩展。Queue 与 Exchange 在
 * {@link MedConsultMqAutoConfiguration} 用 Bean 声明，应用启动自动创建。
 */
public final class MqConstants {

    private MqConstants() {}

    // ===== Exchange =====
    public static final String EXCHANGE_AI_IMAGING = "medconsult.ai.imaging";
    public static final String EXCHANGE_NOTIFICATION = "medconsult.notification";
    public static final String EXCHANGE_LOG = "medconsult.log";
    public static final String EXCHANGE_DRUG_EVENT = "medconsult.drug.event";
    public static final String EXCHANGE_AI_DEAD_LETTER = "medconsult.ai.dead-letter";

    // ===== Queue =====
    public static final String QUEUE_AI_IMAGE_DETECT = "medconsult.ai.image.detect";
    public static final String QUEUE_NOTIFICATION_SEND = "medconsult.notification.send";
    public static final String QUEUE_AUDIT_LOG = "medconsult.audit.log";
    public static final String QUEUE_AI_CALL_LOG = "medconsult.ai.calllog";
    public static final String QUEUE_DRUG_STOCK_ALERT = "medconsult.drug.stock.alert";
    public static final String QUEUE_AI_DEAD_LETTER = "medconsult.ai.dead-letter";

    // ===== RoutingKey =====
    public static final String RK_AI_IMAGE_DETECT = "ai.image.detect";
    public static final String RK_NOTIFICATION_SEND = "notification.send";
    public static final String RK_AUDIT_LOG = "audit.log";
    public static final String RK_AI_CALL_LOG = "ai.calllog";
    public static final String RK_DRUG_STOCK_ALERT = "drug.stock.alert";
    public static final String RK_AI_DEAD_LETTER = "ai.dead-letter";

    /** 消费者幂等 Redis key 前缀（§6.1 Redis SETNX 去重） */
    public static final String IDEMPOTENT_KEY_PREFIX = "medconsult:idempotent:mq:";
}
