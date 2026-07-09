package com.medconsult.notification.consumer;

import lombok.Data;

/**
 * 审计事件消息体（MQ payload 反序列化目标）。
 *
 * <p>生产端（未来各服务 AOP @AuditLog 拦截器）把审计事件序列化为 JSON 发到 audit.log 队列；
 * 本类是反序列化目标，字段与 {@link com.medconsult.notification.audit.dto.AuditLogDTO.WriteRequest} 对齐。
 */
@Data
public class AuditLogEvent {
    private String traceId;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String action;
    private String operatorId;
    private String operatorRole;
    private String operatorName;
    private Long targetOwnerId;
    private String detail;
    private String ip;
    private String userAgent;
    private String result;
}
