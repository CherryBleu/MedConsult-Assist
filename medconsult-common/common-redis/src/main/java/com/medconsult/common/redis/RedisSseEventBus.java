package com.medconsult.common.redis;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 基于 Redis Pub/Sub 的 SSE 事件总线实现（架构文档 §9.2）。
 *
 * <p>publish：直接 {@code convertAndSend(channel, event)} 发到 Redis channel。
 * <p>subscribe：注册到 {@link RedisMessageListenerContainer}，收到消息回调 handler。
 *
 * <p>同一 channel 可被多实例订阅（每个实例一个本地 handler），Redis 广播到全部。
 * 持有对应 SSE 连接的实例在 handler 里 send，其他实例的 handler 收到后无操作（或丢弃）。
 *
 * <p>线程模型：Redis listener 在独立线程回调，handler 内的 emitter.send 在该线程执行。
 * 若 send 阻塞，可能影响该 listener 线程其他消息——handler 应快速返回，重活异步。
 */
public class RedisSseEventBus implements SseEventBus {

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;

    /** channel → 当前实例注册的 handler（用于取消订阅；同 channel 多 handler 用列表） */
    private final ConcurrentHashMap<String, MessageListener> listeners = new ConcurrentHashMap<>();

    public RedisSseEventBus(StringRedisTemplate redis, RedisMessageListenerContainer listenerContainer) {
        this.redis = redis;
        this.listenerContainer = listenerContainer;
    }

    @Override
    public void publish(String channel, String event) {
        redis.convertAndSend(channel, event);
    }

    @Override
    public void subscribe(String channel, Consumer<String> handler) {
        MessageListener listener = (Message message, byte[] pattern) -> {
            String body = new String(message.getBody());
            handler.accept(body);
        };
        // 同 channel 重复订阅时先移除旧 listener（典型场景：连接断开重建）
        MessageListener prev = listeners.put(channel, listener);
        if (prev != null) {
            listenerContainer.removeMessageListener(prev);
        }
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
    }
}
