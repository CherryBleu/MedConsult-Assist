package com.medconsult.common.redis;

/**
 * SSE 事件总线（架构文档 §9.2 关键设计）。
 *
 * <p>解决多实例下 SSE 长连接的实例亲和性问题：
 * <pre>
 *   [实例A] 持有 userId=42 的 SseEmitter
 *      └─ 订阅 channel=pubsub:sse:42
 *   [实例B] AI 任务完成，需通知 userId=42
 *      └─ publish 到 channel=pubsub:sse:42
 *   [Redis Pub/Sub] 广播到所有订阅者
 *   [实例A] 收到 → 本地 emitter.send()
 *   [实例B/C] 也收到但未持有该连接 → 忽略
 * </pre>
 *
 * <p><b>为什么不粘性路由</b>（架构文档 §9.2 已论证）：
 * 粘性路由使某用户绑死单实例，扩缩容连接重分布成本高，宕机时连接全丢无 fallback。
 * Redis Pub/Sub 是云原生标准做法，无亲和性约束。
 */
public interface SseEventBus {

    /**
     * 发布事件到指定 channel（通常 channel = pubsub:sse:{userId}）。
     * 所有订阅该 channel 的实例都会收到。
     *
     * @param channel 频道
     * @param event   事件载荷（JSON 字符串或简单文本）
     */
    void publish(String channel, String event);

    /**
     * 订阅 channel。收到消息时回调 handler。
     *
     * @param channel 频道
     * @param handler 消息处理器（持有 SSE 连接的实例在此 send）
     */
    void subscribe(String channel, java.util.function.Consumer<String> handler);

    /**
     * 取消订阅 channel（移除本实例此前通过 {@link #subscribe} 注册的 handler）。
     *
     * <p>SSE 连接结束（完成/超时/出错）后必须调用，否则 Redis 监听器会随连接数持续累积泄漏。
     *
     * @param channel 频道
     */
    default void unsubscribe(String channel) {
        // 默认空实现：保持向后兼容（旧实现不管理监听器生命周期）
    }
}
