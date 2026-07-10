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
 * <p><b>幂等：原子 SETNX 先行（{@code executeOnce}）</b>。
 * <p>早期版本曾用"isAlreadyProcessed → 业务 → markProcessed"三步法，意图规避 SETNX 先行
 * 在业务失败时的"误标已处理"。但三步法是<b>非原子</b>的 TOCTOU：多实例消费同一条重复消息时
 * （架构文档 §6.3 明确"发送端不选主，重复靠消费者幂等吸收"，多实例并发消费是常态），
 * 两个消费者都能通过 isAlreadyProcessed=false 的检查，各自执行业务 → <b>产生重复通知</b>。
 * <p>{@code executeOnce} 用单条 SETNX 原子地"占位"，只有首个消费者进业务；且内部 try-finally
 * 在业务抛异常时删除占位，允许 MQ 重投——既原子又不会误标。故改回此模式。
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

        // 原子幂等：SETNX 先行，首个消费者进业务，重复消费者拿到 null 跳过。
        // 业务抛异常时 executeOnce 内部删除占位，允许 MQ 重投重试。
        Integer done = idempotentConsumer.executeOnce(idemKey, IDEMPOTENT_WINDOW, () -> {
            NotificationDTO.CreateRequest req = new NotificationDTO.CreateRequest();
            req.setReceiverId(event.getReceiverId());
            req.setReceiverRole(event.getReceiverRole());
            req.setType(event.getType());
            req.setTitle(event.getTitle());
            req.setContent(event.getContent());
            req.setRelatedType(event.getRelatedType());
            req.setRelatedId(event.getRelatedId());
            notificationService.create(req);
            return 1;
        });
        if (done == null) {
            log.info("通知消息重复，跳过: idemKey={}", idemKey);
        } else {
            log.info("通知消息消费成功: idemKey={} receiverId={} type={}",
                    idemKey, event.getReceiverId(), event.getType());
        }
    }
}
