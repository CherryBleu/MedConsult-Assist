package com.medconsult.common.mq.audit;

import lombok.Data;

/**
 * Audit event payload consumed by notification-service and persisted to
 * audit_log.
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
