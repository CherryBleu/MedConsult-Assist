package com.medconsult.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.redis.SseEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI SSE 事件总线适配器（架构文档 §9.2）。
 *
 * <p>桥接 ai-service 的 {@code register(userId, streamId, emitter)} /
 * {@code publish(userId, streamId, event, data)} 高层 API 到 common-redis 的底层
 * {@link SseEventBus}（Redis Pub/Sub 扇出）。
 *
 * <p><b>为什么需要适配器</b>：common-redis 的 {@code SseEventBus} 只做 Redis pub/sub 广播
 * （{@code publish(channel, json)} / {@code subscribe(channel, handler)}），不管理
 * {@link SseEmitter} 生命周期。本类在本实例持有 emitter，订阅对应 channel，
 * 收到 Redis 广播后调用 {@code emitter.send()}，实现多实例下 SSE 长连接的实例亲和性。
 *
 * <p>事件载荷格式：{@code {"event":"start|delta|result|done|error","data":...}}，
 * done/error 事件触发 emitter.complete()。
 */
@Component
public class AiSseEventBus {
    private static final Logger log = LoggerFactory.getLogger(AiSseEventBus.class);

    /** Redis channel 前缀（与架构文档 §9.2 pubsub:sse:{userId} 约定一致） */
    private static final String CHANNEL_PREFIX = "pubsub:sse:";

    private final SseEventBus redisBus;
    private final ObjectMapper objectMapper;

    /** streamId → 当前实例持有的 emitter（多实例下只有持有连接的实例会 send） */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public AiSseEventBus(SseEventBus redisBus, ObjectMapper objectMapper) {
        this.redisBus = redisBus;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册一个 SSE 连接：持有 emitter，订阅对应 channel 等待广播。
     *
     * @param userId   用户 ID（channel 维度）
     * @param streamId 流 ID（emitter 维度，同用户可多流）
     * @param emitter  SSE 发射器
     */
    public void register(String userId, String streamId, SseEmitter emitter) {
        emitters.put(streamId, emitter);
        String channel = channel(userId, streamId);
        // emitter 生命周期结束（完成/超时/出错）时，同时移除本地 emitter 并取消 Redis 订阅，
        // 否则 Redis 监听器会随连接数持续累积泄漏。
        Runnable cleanup = () -> {
            emitters.remove(streamId);
            redisBus.unsubscribe(channel);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        // 订阅 channel：收到 Redis 广播后，若本实例持有该 streamId 的 emitter 则 send
        redisBus.subscribe(channel, json -> {
            SseEmitter local = emitters.get(streamId);
            if (local == null) {
                return; // 本实例不持有该连接，忽略（其他实例会处理）
            }
            SsePayload payload = parse(json);
            if (payload == null) {
                return;
            }
            try {
                local.send(SseEmitter.event().name(payload.event()).data(payload.dataJson()));
                if ("done".equals(payload.event()) || "error".equals(payload.event())) {
                    local.complete();
                    emitters.remove(streamId);
                    redisBus.unsubscribe(channel);
                }
            } catch (IOException | IllegalStateException ex) {
                log.debug("sse send failed, streamId={}", streamId, ex);
                emitters.remove(streamId);
                redisBus.unsubscribe(channel);
            }
        });
    }

    /**
     * 发布事件：序列化后通过 Redis pub/sub 广播，所有实例收到，持有连接的那个执行 send。
     *
     * @param userId   用户 ID
     * @param streamId 流 ID
     * @param event    事件名（start / delta / result / done / error）
     * @param data     事件数据（任意可序列化对象）
     */
    public void publish(String userId, String streamId, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(new SsePayload(event, objectMapper.writeValueAsString(data)));
            redisBus.publish(channel(userId, streamId), json);
        } catch (JsonProcessingException ex) {
            log.warn("sse publish serialize failed, streamId={}, event={}", streamId, event, ex);
        }
    }

    private static String channel(String userId, String streamId) {
        return CHANNEL_PREFIX + userId + ":" + streamId;
    }

    private SsePayload parse(String json) {
        try {
            return objectMapper.readValue(json, SsePayload.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /** Redis 广播载荷（event + dataJson，dataJson 是 data 对象的 JSON 字符串） */
    private record SsePayload(String event, String dataJson) {
        // Jackson 反序列化需要（record 自带构造）
        public String event() { return event; }
        public String dataJson() { return dataJson; }
    }
}
