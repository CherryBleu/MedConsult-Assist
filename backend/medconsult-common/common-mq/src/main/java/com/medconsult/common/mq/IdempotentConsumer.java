package com.medconsult.common.mq;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 消费者幂等助手（架构文档 §6.1）。
 *
 * <p>基于 Redis SETNX：以 messageNo 为去重键，首次消费先写短租约 {@code PROCESSING}，
 * 业务成功后再提升为长窗口 {@code DONE}。重复消费只有读到 DONE（或旧版值 {@code 1}）
 * 才会跳过；读到 PROCESSING 或未知状态会抛异常，让 listener 重试而不是错误 ACK。
 *
 * <p>这是本地消息表可靠投递的<b>消费端闭环</b>（架构文档 §6.3）：
 * 多实例同时扫描投递、或 MQ 重投，都会产生重复消息，靠本类吸收去重。
 * 发送端不去重（§6.3：本地消息表扫描不选主，重复靠消费者幂等吸收）。
 */
public class IdempotentConsumer {

    /** 默认幂等窗口：72 小时。覆盖 MQ 最长重投周期 + 跨日补偿。 */
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(72);
    /** 处理中占位使用短租约，进程崩溃后最终仍可重新消费。 */
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_DONE = "DONE";
    private static final String LEGACY_STATE_DONE = "1";

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
     * @return action 的返回值；仅当状态为 DONE 时返回 null（业务可据 null 判定跳过）
     */
    public <T> T executeOnce(String messageNo, Supplier<T> action) {
        return executeOnce(messageNo, DEFAULT_WINDOW, action);
    }

    public <T> T executeOnce(String messageNo, Duration window, Supplier<T> action) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        ValueOperations<String, String> values = redis.opsForValue();
        Boolean acquired = values.setIfAbsent(key, STATE_PROCESSING, PROCESSING_LEASE);
        if (!Boolean.TRUE.equals(acquired)) {
            String state = values.get(key);
            if (STATE_DONE.equals(state) || LEGACY_STATE_DONE.equals(state)) {
                return null;
            }
            throw new IllegalStateException("MQ message is still processing; retry later");
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException | Error failure) {
            clearProcessingMarker(key, failure);
            throw failure;
        }

        // 写 DONE 失败时保留短租约并传播异常，绝不能把未完成幂等落标的消息 ACK。
        values.set(key, STATE_DONE, window);
        return result;
    }

    /**
     * 仅判断是否已处理过（不执行）。
     */
    public boolean isAlreadyProcessed(String messageNo) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        String state = redis.opsForValue().get(key);
        return STATE_DONE.equals(state) || LEGACY_STATE_DONE.equals(state);
    }

    /**
     * 标记为已处理（业务自行管理幂等窗口时用）。
     */
    public void markProcessed(String messageNo, Duration window) {
        String key = MqConstants.IDEMPOTENT_KEY_PREFIX + messageNo;
        redis.opsForValue().set(key, STATE_DONE, window);
    }

    private void clearProcessingMarker(String key, Throwable failure) {
        try {
            redis.delete(key);
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
