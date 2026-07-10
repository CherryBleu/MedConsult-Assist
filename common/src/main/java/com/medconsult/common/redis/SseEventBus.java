package com.medconsult.common.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEventBus implements MessageListener {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHANNEL_PREFIX = "pubsub:sse:";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final String keyPrefix;

    public SseEventBus(StringRedisTemplate redisTemplate,
                       RedisMessageListenerContainer listenerContainer,
                       SseChannelProperties properties) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.keyPrefix = properties.keyPrefix() == null ? "medical:" : properties.keyPrefix();
    }

    public void register(String userId, String streamId, SseEmitter emitter) {
        String emitterKey = emitterKey(userId, streamId);
        emitters.computeIfAbsent(emitterKey, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        listenerContainer.addMessageListener(this, new ChannelTopic(channel(userId)));
        emitter.onCompletion(() -> unregister(emitterKey, emitter));
        emitter.onTimeout(() -> unregister(emitterKey, emitter));
        emitter.onError(ignored -> unregister(emitterKey, emitter));
    }

    public void publish(String userId, String streamId, String event, Object data) {
        try {
            redisTemplate.convertAndSend(channel(userId), MAPPER.writeValueAsString(new SseEnvelope(userId, streamId, event, data)));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to serialize SSE event", ex);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = MAPPER.readTree(message.getBody());
            SseEnvelope envelope = new SseEnvelope(
                    root.path("userId").asText(),
                    root.path("streamId").asText(),
                    root.path("event").asText(),
                    MAPPER.convertValue(root.path("data"), Object.class)
            );
            CopyOnWriteArrayList<SseEmitter> localEmitters = emitters.get(emitterKey(envelope.userId(), envelope.streamId()));
            if (localEmitters == null || localEmitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : localEmitters) {
                send(envelope, emitter);
            }
        } catch (IOException ignored) {
            // Ignore malformed pub/sub payloads from old deployments.
        }
    }

    private void send(SseEnvelope envelope, SseEmitter emitter) {
        String emitterKey = emitterKey(envelope.userId(), envelope.streamId());
        try {
            emitter.send(SseEmitter.event().name(envelope.event()).data(MAPPER.writeValueAsString(envelope.data())));
            if ("done".equals(envelope.event()) || "error".equals(envelope.event())) {
                emitter.complete();
                unregister(emitterKey, emitter);
            }
        } catch (IOException | IllegalStateException ex) {
            unregister(emitterKey, emitter);
        }
    }

    private void unregister(String emitterKey, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> localEmitters = emitters.get(emitterKey);
        if (localEmitters == null) {
            return;
        }
        localEmitters.remove(emitter);
        if (localEmitters.isEmpty()) {
            emitters.remove(emitterKey);
        }
    }

    private String channel(String userId) {
        return keyPrefix + CHANNEL_PREFIX + normalizeUser(userId);
    }

    private static String emitterKey(String userId, String streamId) {
        return normalizeUser(userId) + ":" + streamId;
    }

    private static String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }

    private record SseEnvelope(String userId, String streamId, String event, Object data) {
    }
}
