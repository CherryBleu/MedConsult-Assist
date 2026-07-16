package com.medconsult.common.mq;

import lombok.Data;

/**
 * 审计事件消息体（MQ payload 序列化/反序列化目标）。
 *
 * <p>生产端：业务服务 {@code @AuditLog} AOP 切面构造本对象，序列化为 JSON 发到
 * {@link MqConstants#EXCHANGE_LOG} / {@link MqConstants#RK_AUDIT_LOG}（经本地消息表
 * {@code local_message} + {@link MessageDispatcher} 可靠投递）。
 *
 * <p>消费端：notification-service 的 {@code AuditLogConsumer} 反序列化本对象写 audit_log 表。
 *
 * <p><b>本类原在 notification-service/consumer 包，2026-07-16 提取到 common-mq</b>，
 * 让生产端（各业务服务）与消费端（notification）共享同一类型，避免字段漂移。
 * 字段与 notification {@code AuditLogDTO.WriteRequest} 一一对应。
 *
 * <p>字段命名规则：消费端用 Jackson 按<b>字段名</b>反序列化，两端必须一致
 * （如 {@code resourceType} 非 {@code resource_type}，{@code targetOwnerId} 是 Long）。
 * 仅 {@code resourceType}/{@code action} 业务必填（对应 audit_log 表 NOT NULL 列），
 * 其余可空；{@code id}/{@code auditNo}/{@code createdAt} 由消费端生成，不在本事件中。
 */
@Data
public class AuditLogEvent {
    /** 链路追踪 ID（跨服务串联） */
    private String traceId;
    /** 资源类型：PATIENT/MEDICAL_RECORD/PRESCRIPTION/DRUG/SCHEDULE...（业务必填） */
    private String resourceType;
    /** 资源业务编号 */
    private String resourceId;
    /** 资源名称冗余（便于检索） */
    private String resourceName;
    /** 操作类型 VIEW/CREATE/UPDATE/DELETE/EXPORT/LOGIN/LOGOUT（业务必填） */
    private String action;
    /** 操作人 ID */
    private String operatorId;
    /** 操作人角色 */
    private String operatorRole;
    /** 操作人姓名冗余 */
    private String operatorName;
    /** 资源所属患者 ID（便于按患者检索审计） */
    private Long targetOwnerId;
    /** 变更前后快照（JSON 串） */
    private String detail;
    /** 操作 IP */
    private String ip;
    /** User-Agent */
    private String userAgent;
    /** 结果 SUCCESS/FAILED（可空，消费端默认 SUCCESS） */
    private String result;
}
