package com.medconsult.notification.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.redis.SseEventBus;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRealtimeService {

    private static final Long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final String CHANNEL_PREFIX = "medconsult:pubsub:notification:";

    private final SseEventBus eventBus;
    private final ObjectMapper objectMapper;
    private final Map<String, ChannelState> channels = new ConcurrentHashMap<>();

    public SseEmitter subscribe(JwtPayload payload) {
        Set<String> channelNames = resolveChannels(payload);
        if (channelNames.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前登录态缺少通知接收人标识，请重新登录后再试");
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString().replace("-", "");
        List<ChannelState> registered = new ArrayList<>();
        for (String channel : channelNames) {
            registered.add(register(channel, emitterId, emitter));
        }

        log.info("通知实时订阅建立: emitterId={} userNo={} role={} channels={}",
                emitterId, payload.userNo(), payload.primaryRole(), channelNames);
        Runnable cleanup = () -> registered.forEach(state -> unregister(state, emitterId));
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        send(emitter, "connected", "{}");
        return emitter;
    }

    public void publishCreatedAfterCommit(Notification notification) {
        afterCommit(() -> publish(notification.getReceiverId(), notification.getReceiverRole(), "notification", new NotificationDTO.ListItem(
                notification.getNotificationNo(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                false,
                notification.getCreatedAt()
        )));
    }

    public void publishReadAfterCommit(Notification notification) {
        afterCommit(() -> publish(notification.getReceiverId(), notification.getReceiverRole(), "notification-read", Map.of(
                "notificationId", notification.getNotificationNo(),
                "read", true
        )));
    }

    private void publish(String receiverId, String receiverRole, String event, Object data) {
        if (receiverId == null || receiverId.isBlank()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(new StreamPayload(event, objectMapper.writeValueAsString(data)));
            log.info("通知实时事件发布: receiverId={} event={}", receiverId, event);
            eventBus.publish(channel(receiverId), payload);
        } catch (JsonProcessingException ex) {
            log.warn("通知实时事件序列化失败: receiverId={} event={}", receiverId, event, ex);
        }
    }

    private ChannelState register(String channel, String emitterId, SseEmitter emitter) {
        synchronized (channels) {
            ChannelState state = channels.get(channel);
            if (state == null) {
                state = new ChannelState(channel);
                ChannelState subscribed = state;
                eventBus.subscribe(channel, json -> deliver(subscribed, json));
                channels.put(channel, state);
            }
            state.emitters.put(emitterId, emitter);
            return state;
        }
    }

    private void unregister(ChannelState state, String emitterId) {
        synchronized (channels) {
            state.emitters.remove(emitterId);
            if (state.emitters.isEmpty() && channels.remove(state.channel, state)) {
                eventBus.unsubscribe(state.channel);
            }
        }
    }

    private void deliver(ChannelState state, String json) {
        StreamPayload payload;
        try {
            payload = objectMapper.readValue(json, StreamPayload.class);
        } catch (JsonProcessingException ex) {
            log.debug("忽略无法解析的通知实时事件: channel={}", state.channel, ex);
            return;
        }

        List<Map.Entry<String, SseEmitter>> emitters;
        synchronized (channels) {
            emitters = List.copyOf(state.emitters.entrySet());
        }
        log.info("通知实时事件投递: channel={} event={} emitters={}",
                state.channel, payload.event(), emitters.size());
        for (Map.Entry<String, SseEmitter> entry : emitters) {
            try {
                send(entry.getValue(), payload.event(), payload.dataJson());
            } catch (Exception ex) {
                log.debug("通知实时事件投递失败，移除 SSE 连接: channel={} emitterId={}",
                        state.channel, entry.getKey(), ex);
                unregister(state, entry.getKey());
            }
        }
    }

    private void send(SseEmitter emitter, String event, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(event).data(dataJson));
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("SSE send failed", ex);
        }
    }

    private static Set<String> resolveChannels(JwtPayload payload) {
        Set<String> result = new LinkedHashSet<>();
        if (payload != null) {
            addIfPresent(result, channel(payload.primaryRole()));
            if (payload.roles() != null) {
                payload.roles().forEach(role -> addIfPresent(result, channel(role)));
            }
        }
        for (String receiverId : receiverCandidates(payload)) {
            result.add(channel(receiverId));
        }
        return result;
    }

    private static Set<String> receiverCandidates(JwtPayload payload) {
        Set<String> candidates = new LinkedHashSet<>();
        if (payload == null) {
            return candidates;
        }
        addIfPresent(candidates, payload.userNo());
        if (payload.patientId() != null) {
            candidates.add(String.valueOf(payload.patientId()));
        }
        if (payload.doctorId() != null) {
            candidates.add(String.valueOf(payload.doctorId()));
        }
        if (payload.pharmacistId() != null) {
            candidates.add(String.valueOf(payload.pharmacistId()));
        }
        return candidates;
    }

    private static void addIfPresent(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value);
        }
    }

    private static String channel(String receiverId) {
        return CHANNEL_PREFIX + receiverId;
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record StreamPayload(String event, String dataJson) {
    }

    private static final class ChannelState {
        private final String channel;
        private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

        private ChannelState(String channel) {
            this.channel = channel;
        }
    }
}
