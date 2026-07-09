package com.medconsult.common.mq;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 消费者幂等助手（架构文档 §6.1）。
 *
 * <p>基于 Redis SETNX：以 messageNo 为去重键，首次消费写入"已处理"标记（带 TTL），
 * 重复消费（同 messageNo）直接判定为已处理，跳过业务逻辑。
 *
 * <p>这是本地消息表可靠投递的<b>消费端闭环</b>（架构文档 §6.3）：
 * 多实例同时扫描投递、或 MQ 重投，都会产生重复消息，靠本类吸收去重。
 * 发送端不去重（§6.3：本地消息表扫描不选主，重复靠消费者幂等吸收）。
 */
public class IdempotentConsumer {

    /** 默认幂等窗口：72 小时。覆盖 MQ 最长重投周期 + 跨日补偿。 */
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(72);

    private final StringRedisTemplate redis;

    public IdempotentConsumer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 幂等执行。
     *
     * @param messageNo 业务唯一键（来自 local_message 或消息头）
     * @param action    实际业务逻辑（仅在首次执行时调用）
     * @param <T>       返回类型
     * @return action 的返回值；若已处理过则返回 null（业务可据 null 判定跳过）
     */
    public <T> T executeOnce(String messageNo, Supplier<T> action) {
        return executeOnce(messageNo, DEFAULT_WINDOW, action);
    }

    public <T> T executeOnce(String messageNo, Duration window, Supplier<T> action) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        // SETNX：仅当 key 不存在时设置，返回 true=首次
        Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", window);
        if (Boolean.TRUE.equals(firstTime)) {
            // 关键：action 失败必须回滚幂等标记，否则 MQ 重投会被判"已处理"→ 消息永久丢失。
            // 推荐消费者改用 isAlreadyProcessed + 业务 + markProcessed 三步法（见 NotificationConsumer），
            // 本方法保留 try-finally 兜底：action 抛异常时删除标记，允许 MQ 重投重试。
            try {
                return action.get();
            } catch (RuntimeException e) {
                redis.delete(key);
                throw e;
            }
        }
        // 已处理：幂等跳过
        return null;
    }

    /**
     * 仅判断是否已处理过（不执行）。
     */
    public boolean isAlreadyProcessed(String messageNo) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /**
     * 标记为已处理（业务自行管理幂等窗口时用）。
     */
    public void markProcessed(String messageNo, Duration window) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        redis.opsForValue().set(key, "1", window);
    }
}
