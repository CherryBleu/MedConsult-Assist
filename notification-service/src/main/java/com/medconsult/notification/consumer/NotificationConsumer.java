package com.medconsult.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.IdempotentConsumer;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.notification.notification.dto.NotificationDTO;
import com.medconsult.notification.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 通知消息消费者（架构文档 §6.1 / §7.5）。
 *
 * <p>监听 {@link MqConstants#QUEUE_NOTIFICATION_SEND}，消费通知事件写库。
 *
 * <p><b>幂等三步法</b>（避免 IdempotentConsumer.executeOnce 的 SETNX 先行误标坑）：
 * <ol>
 *   <li>{@code isAlreadyProcessed(messageNo)} → true 直接跳过（重复消息）</li>
 *   <li>执行业务（反序列化 + 调 NotificationService.create）；失败抛异常 → 不 ack → MQ 重投</li>
 *   <li>业务成功后 {@code markProcessed(messageNo, 72h)}</li>
 * </ol>
 * <p>幂等键：消息头 {@code messageNo}（生产端 MessageDispatcher setHeader 了），
 *    回退用 receiverId + title 拼接（兼容无消息头的直发场景）。
 * <p>注意：fallback 路径下，72h 内同 (receiverId, title) 的不同通知会被去重。
 * 实际生产路径 Dispatcher 总会 setHeader messageNo（唯一），fallback 几乎不触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final Duration IDEMPOTENT_WINDOW = Duration.ofHours(72);

    private final IdempotentConsumer idempotentConsumer;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @RabbitListener(queues = MqConstants.QUEUE_NOTIFICATION_SEND)
    public void onNotification(String payload,
                               @Header(name = "messageNo", required = false) String messageNo) throws Exception {
        NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);

        // 幂等键：优先消息头，回退业务字段拼接
        String idemKey = (messageNo != null && !messageNo.isBlank())
                ? messageNo
                : "notification:" + event.getReceiverId() + ":" + event.getTitle();

        // 三步法：先判重
        if (idempotentConsumer.isAlreadyProcessed(idemKey)) {
            log.info("通知消息重复，跳过: idemKey={}", idemKey);
            return;
        }

        // 执行业务（失败抛异常 → MQ 重投，不误标幂等）
        NotificationDTO.CreateRequest req = new NotificationDTO.CreateRequest();
        req.setReceiverId(event.getReceiverId());
        req.setReceiverRole(event.getReceiverRole());
        req.setType(event.getType());
        req.setTitle(event.getTitle());
        req.setContent(event.getContent());
        req.setRelatedType(event.getRelatedType());
        req.setRelatedId(event.getRelatedId());
        notificationService.create(req);

        // 业务成功后标记幂等
        idempotentConsumer.markProcessed(idemKey, IDEMPOTENT_WINDOW);
        log.info("通知消息消费成功: idemKey={} receiverId={} type={}",
                idemKey, event.getReceiverId(), event.getType());
    }
}
