package com.medconsult.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.mq.IdempotentConsumer;
import com.medconsult.common.mq.MqConstants;
import com.medconsult.notification.audit.dto.AuditLogDTO;
import com.medconsult.notification.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 审计日志消息消费者（架构文档 §7.5）。
 *
 * <p>监听 {@link MqConstants#QUEUE_AUDIT_LOG}，消费审计事件写 audit_log。
 *
 * <p><b>幂等：原子 SETNX 先行（{@code executeOnce}）</b>。
 * <p>同 NotificationConsumer：早期用"判重→业务→标记"三步法，但多实例并发消费同一重复消息时
 * 存在 TOCTOU 窗口（两消费者都过判重 → 各自写库 → 审计日志重复）。改用原子 SETNX 先行。
 * <p>幂等键：消息头 messageNo，回退 resourceType+resourceId+action+operatorId 拼接。
 * <p>注意：fallback 路径下，72h 内同 (resourceType, resourceId, action, operatorId) 的不同审计事件会被去重。
 * 实际生产路径 Dispatcher 总会 setHeader messageNo（唯一），fallback 几乎不触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private static final Duration IDEMPOTENT_WINDOW = Duration.ofHours(72);

    private final IdempotentConsumer idempotentConsumer;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @RabbitListener(queues = MqConstants.QUEUE_AUDIT_LOG)
    public void onAuditLog(String payload,
                           @Header(name = "messageNo", required = false) String messageNo) throws Exception {
        AuditLogEvent event = objectMapper.readValue(payload, AuditLogEvent.class);

        // 幂等键：优先消息头，回退业务字段拼接
        String idemKey = (messageNo != null && !messageNo.isBlank())
                ? messageNo
                : "audit:" + event.getResourceType() + ":" + event.getResourceId()
                        + ":" + event.getAction() + ":" + event.getOperatorId();

        // 原子幂等：SETNX 先行，首个消费者进业务，重复消费者拿到 null 跳过。
        // 业务抛异常时 executeOnce 内部删除占位，允许 MQ 重投重试。
        Integer done = idempotentConsumer.executeOnce(idemKey, IDEMPOTENT_WINDOW, () -> {
            AuditLogDTO.WriteRequest req = new AuditLogDTO.WriteRequest();
            req.setTraceId(event.getTraceId());
            req.setResourceType(event.getResourceType());
            req.setResourceId(event.getResourceId());
            req.setResourceName(event.getResourceName());
            req.setAction(event.getAction());
            req.setOperatorId(event.getOperatorId());
            req.setOperatorRole(event.getOperatorRole());
            req.setOperatorName(event.getOperatorName());
            req.setTargetOwnerId(event.getTargetOwnerId());
            req.setDetail(event.getDetail());
            req.setIp(event.getIp());
            req.setUserAgent(event.getUserAgent());
            req.setResult(event.getResult());
            auditLogService.write(req);
            return 1;
        });
        if (done == null) {
            log.info("审计消息重复，跳过: idemKey={}", idemKey);
        } else {
            log.info("审计消息消费成功: idemKey={} resourceType={} action={}",
                    idemKey, event.getResourceType(), event.getAction());
        }
    }
}
