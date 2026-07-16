package com.medconsult.common.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 审计日志生产者：把 {@link AuditLogEvent} 落入本地消息表 {@code local_message}，
 * 由 {@link MessageDispatcher}（5s 周期扫描）异步投递到 audit.log 队列。
 *
 * <p><b>事务边界</b>：本方法不开事务。调用方（通常是 {@code AuditLogAspect} 在 Controller
 * proceed 成功后调用）应保证"业务成功才发审计"——业务失败（抛异常）则不会走到这里。
 * 详见 AuditLogAspect 的设计说明（残余风险：insert 后进程崩溃则该审计丢失，窗口极小）。
 *
 * <p><b>幂等</b>：每条审计生成唯一 {@code messageNo}（UUID），Dispatcher 投递时 setHeader，
 * 消费端 {@code AuditLogConsumer} 据此做 SETNX 幂等（72h 窗口），避免重投导致审计重复。
 *
 * <p><b>失败容忍</b>：审计是辅助链路，绝不阻断主业务。序列化/入库失败仅记日志，
 * 不向上抛异常（主业务已成功，不能因审计失败而让接口报错）。
 *
 * <p>Bean 由 {@link MedConsultMqAutoConfiguration} 注册（common-mq 包默认不被业务服务
 * @SpringBootApplication 扫描），不在此处标 @Component。
 */
@Slf4j
@RequiredArgsConstructor
public class AuditLogProducer {

    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 发布审计事件到本地消息表。
     *
     * @param event 审计事件（resourceType/action 应已填充）
     */
    public void publish(AuditLogEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            String messageNo = "audit-" + UUID.randomUUID();
            LocalMessage msg = LocalMessage.of(
                    MqConstants.EXCHANGE_LOG, messageNo, MqConstants.RK_AUDIT_LOG, payloadJson);
            localMessageMapper.insert(msg);
        } catch (JsonProcessingException e) {
            // 序列化失败属编程错误，记录但不阻断主业务
            log.error("审计事件序列化失败，跳过此条审计: resourceType={} action={}",
                    event.getResourceType(), event.getAction(), e);
        } catch (RuntimeException e) {
            // 入库失败（如库不可用）不阻断主业务；审计丢失可接受，仅记日志
            log.error("审计事件落 local_message 失败，跳过此条审计: resourceType={} action={}",
                    event.getResourceType(), event.getAction(), e);
        }
    }
}
