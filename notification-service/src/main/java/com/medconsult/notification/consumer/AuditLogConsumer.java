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
 * <p><b>幂等三步法</b>（同 NotificationConsumer）：
 * <ol>
 *   <li>{@code isAlreadyProcessed(messageNo)} → true 跳过</li>
 *   <li>执行业务（反序列化 + 调 AuditLogService.write）；失败抛异常 → MQ 重投</li>
 *   <li>业务成功后 {@code markProcessed(messageNo, 72h)}</li>
 * </ol>
 * <p>幂等键：消息头 messageNo，回退 resourceType+resourceId+action+operatorId+createdAt 拼接。
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

        // 三步法：先判重
        if (idempotentConsumer.isAlreadyProcessed(idemKey)) {
            log.info("审计消息重复，跳过: idemKey={}", idemKey);
            return;
        }

        // 执行业务（失败抛异常 → MQ 重投，不误标幂等）
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

        // 业务成功后标记幂等
        idempotentConsumer.markProcessed(idemKey, IDEMPOTENT_WINDOW);
        log.info("审计消息消费成功: idemKey={} resourceType={} action={}",
                idemKey, event.getResourceType(), event.getAction());
    }
}
